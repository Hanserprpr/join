package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.CheckInQrGrant;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentApplicationMapper;
import cn.sduonline.join.mapper.DepartmentCheckInMapper;
import cn.sduonline.join.mapper.DepartmentInterviewSessionMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Exercises the service through Spring transactions and real mapper SQL, not mocked locks. */
class DepartmentCheckInConcurrencyTest {

    private Connection keeper;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private DepartmentCheckInService service;
    private InterviewSseService sse;
    private final ExecutorService workers = Executors.newFixedThreadPool(8);
    private volatile Runnable beforeSessionLock = () -> {};
    private volatile Runnable beforeInsert = () -> {};

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:check-in-concurrency-" + UUID.randomUUID()
                + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keeper = database.getConnection();
        // Start connections at the production default. The service must override it.
        dataSource = new DelegatingDataSource(database) {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                return connection;
            }
        };
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE department (id BIGINT PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE department_application (
                    id BIGINT PRIMARY KEY, department_id BIGINT, cas_id VARCHAR(32),
                    status VARCHAR(32), submitted_at TIMESTAMP,
                    UNIQUE (department_id, cas_id))
                """);
        jdbc.execute("""
                CREATE TABLE department_interview_session (
                    id BIGINT PRIMARY KEY, department_id BIGINT, name VARCHAR(64),
                    starts_at TIMESTAMP, ends_at TIMESTAMP, location VARCHAR(64),
                    check_in_limit INT, qr_check_in_enabled BOOLEAN,
                    qr_code_ttl_seconds INT, status VARCHAR(16),
                    published_at TIMESTAMP, ended_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE department_check_in (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, department_id BIGINT,
                    session_id BIGINT, application_id BIGINT, cas_id VARCHAR(32),
                    checked_in_at TIMESTAMP, queue_number INT, queue_order BIGINT,
                    pass_count INT, priority BOOLEAN, requires_recheck_in BOOLEAN,
                    UNIQUE (session_id, application_id), UNIQUE (session_id, queue_number),
                    FOREIGN KEY (session_id) REFERENCES department_interview_session(id),
                    FOREIGN KEY (application_id) REFERENCES department_application(id))
                """);
        jdbc.execute("""
                CREATE TABLE department_check_in_sequence (
                    session_id BIGINT PRIMARY KEY, next_number INT,
                    FOREIGN KEY (session_id) REFERENCES department_interview_session(id))
                """);
        jdbc.execute("""
                CREATE TABLE department_interview (
                    id BIGINT PRIMARY KEY, application_id BIGINT,
                    check_in_id BIGINT, ended_at TIMESTAMP)
                """);
        jdbc.update("INSERT INTO department VALUES (12)");
        for (int index = 1; index <= 8; index++) {
            jdbc.update("INSERT INTO department_application VALUES (?, 12, ?, 'SUBMITTED', ?)",
                    99L + index, "2024000" + index, LocalDateTime.now());
        }
        for (long sessionId : List.of(30L, 31L)) {
            jdbc.update("""
                    INSERT INTO department_interview_session
                        (id, department_id, starts_at, ends_at, check_in_limit,
                         qr_check_in_enabled, status)
                    VALUES (?, 12, ?, ?, 1, FALSE, 'PUBLISHED')
                    """, sessionId, LocalDateTime.now().minusHours(1),
                    LocalDateTime.now().plusHours(1));
        }

        Configuration configuration = new Configuration(new Environment(
                "concurrency-test", new SpringManagedTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(DepartmentApplicationMapper.class);
        configuration.addMapper(DepartmentCheckInMapper.class);
        configuration.addMapper(DepartmentInterviewSessionMapper.class);
        SqlSessionTemplate sql = new SqlSessionTemplate(
                new SqlSessionFactoryBuilder().build(configuration));
        var sessions = before(sql.getMapper(DepartmentInterviewSessionMapper.class),
                DepartmentInterviewSessionMapper.class, "selectCheckInSessionForUpdate", () -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    try {
                        assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                                DataSourceUtils.getConnection(dataSource).getTransactionIsolation());
                    } catch (SQLException exception) {
                        throw new IllegalStateException(exception);
                    }
                    beforeSessionLock.run();
                });
        var checkIns = before(sql.getMapper(DepartmentCheckInMapper.class),
                DepartmentCheckInMapper.class, "insert", () -> beforeInsert.run());
        var organizations = mock(AdminOrganizationMapper.class);
        when(organizations.selectDepartmentById(12L)).thenAnswer(invocation -> {
            // Preserve the database read before the session lock, including its snapshot.
            Department department = new Department();
            department.setId(jdbc.queryForObject(
                    "SELECT id FROM department WHERE id = 12", Long.class));
            return department;
        });
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> tokens = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(tokens);
        when(tokens.get("join:check-in:token:valid")).thenReturn("12:30");
        sse = mock(InterviewSseService.class);
        var target = new DepartmentCheckInService(organizations,
                sql.getMapper(DepartmentApplicationMapper.class), checkIns, sessions,
                redis, new AppProperties(), sse);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        service = (DepartmentCheckInService) proxy.getProxy();
    }

    @AfterEach
    void tearDown() throws Exception {
        workers.shutdownNow();
        try {
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            if (keeper != null) {
                keeper.close();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "30, 20240002, INTERVIEW_SESSION_FULL",
            "30, 20240001, CHECK_IN_ALREADY_EXISTS",
            "31, 20240001, CHECK_IN_OTHER_SESSION_EXISTS"
    })
    void overlappingRequestsCannotOverbookOrDoubleQueue(
            long secondSession, String secondUser, BizCode expectedError) throws Exception {
        CountDownLatch firstAtInsert = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAtSession = new CountDownLatch(1);
        AtomicInteger insertAttempts = new AtomicInteger();
        AtomicInteger sessionAttempts = new AtomicInteger();
        beforeInsert = () -> {
            if (insertAttempts.incrementAndGet() == 1) {
                firstAtInsert.countDown();
                await(releaseFirst);
            }
        };
        beforeSessionLock = () -> {
            if (sessionAttempts.incrementAndGet() == 2) {
                secondAtSession.countDown();
            }
        };

        Future<ServiceResult<CheckInVO>> first = workers.submit(
                () -> service.checkIn(12L, 30L, null, "20240001"));
        Future<ServiceResult<CheckInVO>> second;
        try {
            await(firstAtInsert);
            second = workers.submit(
                    () -> service.checkIn(12L, secondSession, null, secondUser));
            await(secondAtSession);
            // B has already read the department while A is still uncommitted.
            // Even when B targets a different session, it must wait for A's application lock.
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
        } finally {
            releaseFirst.countDown();
        }
        assertTrue(first.get(10, TimeUnit.SECONDS).isSuccess());
        assertEquals(expectedError, second.get(10, TimeUnit.SECONDS).error());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM department_check_in", Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT next_number FROM department_check_in_sequence WHERE session_id = 30",
                Integer.class));
    }

    @Test
    void concurrentCandidatesReceiveDistinctSequentialNumbers() throws Exception {
        jdbc.update("UPDATE department_interview_session SET check_in_limit = 8 WHERE id = 30");
        CyclicBarrier ready = new CyclicBarrier(8);
        beforeSessionLock = () -> {
            try {
                ready.await(10, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        };
        List<Future<ServiceResult<CheckInVO>>> requests = new ArrayList<>();
        for (int index = 1; index <= 8; index++) {
            String casId = "2024000" + index;
            requests.add(workers.submit(() -> service.checkIn(12L, 30L, null, casId)));
        }
        List<Integer> numbers = new ArrayList<>();
        for (var request : requests) {
            var result = request.get(10, TimeUnit.SECONDS);
            assertTrue(result.isSuccess());
            numbers.add(result.data().queueNumber());
        }
        assertEquals(IntStream.rangeClosed(1, 8).boxed().toList(), numbers.stream().sorted().toList());
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM department_check_in", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"captured", "legacy"})
    void qrEntrypointsUseRealReadCommittedTransactions(String entrypoint) {
        assertTrue(checkIn(entrypoint).isSuccess());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM department_check_in", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "captured", "legacy"})
    void everyEntrypointRollsBackBothCheckInAndSequence(String entrypoint) {
        doThrow(new IllegalStateException("simulated failure after insertion"))
                .when(sse).publishQueueAfterCommit(12L, 30L);
        assertThrows(IllegalStateException.class, () -> checkIn(entrypoint));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM department_check_in", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM department_check_in_sequence", Integer.class));
    }

    private ServiceResult<CheckInVO> checkIn(String entrypoint) {
        return switch (entrypoint) {
            case "direct" -> service.checkIn(12L, 30L, null, "20240001");
            case "captured" -> service.checkInCaptured(new CheckInQrGrant(12L, 30L), "20240001");
            case "legacy" -> service.checkIn("valid", "20240001");
            default -> throw new IllegalArgumentException(entrypoint);
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent request did not reach its checkpoint");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static <T> T before(T delegate, Class<T> type, String methodName, Runnable hook) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    if (method.getName().equals(methodName)) {
                        hook.run();
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }));
    }
}
