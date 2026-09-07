package cn.sduonline.join.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.sduonline.join.data.enums.InterviewSessionStatus;
import java.time.LocalDateTime;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class CheckInRecoveryQueryTest {

    @Test
    void endedSessionRecoveryPersistsWithoutOpeningDraftsOrResettingPassCount()
            throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:recovery-" + java.util.UUID.randomUUID()
                + ";MODE=MySQL");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE department_interview_session (
                        id BIGINT PRIMARY KEY, department_id BIGINT,
                        name VARCHAR(64), starts_at TIMESTAMP, ends_at TIMESTAMP,
                        location VARCHAR(64), check_in_limit INT,
                        qr_check_in_enabled BOOLEAN, qr_code_ttl_seconds INT,
                        status VARCHAR(16), published_at TIMESTAMP, ended_at TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO department_interview_session (id, department_id, status)
                    VALUES (30, 12, 'ENDED'), (31, 12, 'PUBLISHED'), (32, 12, 'DRAFT')
                    """);
            statement.execute("""
                    CREATE TABLE department_check_in (
                        id BIGINT PRIMARY KEY, department_id BIGINT, session_id BIGINT,
                        application_id BIGINT, cas_id VARCHAR(32), checked_in_at TIMESTAMP,
                        queue_number INT, queue_order BIGINT, pass_count INT,
                        priority BOOLEAN, requires_recheck_in BOOLEAN,
                        UNIQUE (session_id, application_id), UNIQUE (session_id, queue_number))
                    """);
            statement.execute("""
                    INSERT INTO department_check_in VALUES
                    (200, 12, 30, 100, '20240001', CURRENT_TIMESTAMP, 1, 1, 2, FALSE, TRUE)
                    """);
            Configuration configuration = new Configuration(new Environment(
                    "test", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(DepartmentInterviewSessionMapper.class);
            configuration.addMapper(DepartmentCheckInMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(configuration);

            try (var session = factory.openSession()) {
                var sessions = session.getMapper(DepartmentInterviewSessionMapper.class);
                assertEquals(InterviewSessionStatus.ENDED,
                        sessions.selectCheckInSessionForUpdate(12L, 30L).getStatus());
                assertEquals(InterviewSessionStatus.PUBLISHED,
                        sessions.selectCheckInSessionForUpdate(12L, 31L).getStatus());
                assertNull(sessions.selectCheckInSessionForUpdate(12L, 32L));
                assertNull(sessions.selectCheckInSessionForUpdate(13L, 30L));
                assertNull(sessions.selectPublishedForUpdate(12L, 30L));

                var checkIns = session.getMapper(DepartmentCheckInMapper.class);
                var existing = checkIns.selectBySessionAndApplication(30L, 100L);
                existing.setQueueNumber(9);
                existing.setQueueOrder(9L);
                existing.setCheckedInAt(LocalDateTime.now());
                assertEquals(1, checkIns.reactivateAfterCheckIn(existing));
                session.commit();
            }
            try (var session = factory.openSession()) {
                var mapper = session.getMapper(DepartmentCheckInMapper.class);
                var restored = mapper.selectBySessionAndApplication(30L, 100L);
                assertEquals(200L, restored.getId());
                assertEquals(9, restored.getQueueNumber());
                assertEquals(9L, restored.getQueueOrder());
                assertEquals(2, restored.getPassCount());
                assertEquals(false, restored.getPriority());
                assertEquals(false, restored.getRequiresRecheckIn());
                assertEquals(0, mapper.reactivateAfterCheckIn(restored));
            }
        }
    }
}
