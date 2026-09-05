package cn.sduonline.join.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.sduonline.join.data.dto.InterviewQueueScope;
import java.sql.DriverManager;
import java.util.Set;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class InterviewQueueScopeQueryTest {

    @Test
    void findsDistinctUnfinishedQueuesAcrossDepartmentsForBothCandidates()
            throws Exception {
        String url = "jdbc:h2:mem:queue-scopes-" + java.util.UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE department_check_in (
                        id BIGINT PRIMARY KEY, department_id BIGINT,
                        session_id BIGINT, cas_id VARCHAR(32))
                    """);
            statement.execute("""
                    CREATE TABLE department_interview (
                        id BIGINT PRIMARY KEY, check_in_id BIGINT,
                        ended_at TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO department_check_in VALUES
                        (1, 12, 5, 'previous'),
                        (2, 13, 6, 'previous'),
                        (3, 14, 7, 'next'),
                        (4, 13, 6, 'next'),
                        (5, 15, 8, 'unrelated'),
                        (6, 16, 9, 'previous'),
                        (7, 12, 10, 'previous')
                    """);
            statement.execute("""
                    INSERT INTO department_interview VALUES
                        (1, 1, CURRENT_TIMESTAMP),
                        (2, 3, NULL),
                        (3, 6, CURRENT_TIMESTAMP)
                    """);
            // 场次表不参与查询，关闭签到的场次也能收到队列变化。
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL(url);
            Configuration configuration = new Configuration(new Environment(
                    "test", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(DepartmentInterviewMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(configuration);
            try (var session = factory.openSession()) {
                var scopes = session.getMapper(DepartmentInterviewMapper.class)
                        .selectCandidateQueueScopes(Set.of("previous", "next"));

                assertEquals(3, scopes.size());
                assertEquals(Set.of(
                        new InterviewQueueScope(13L, 6L),
                        new InterviewQueueScope(14L, 7L),
                        new InterviewQueueScope(12L, 10L)
                ), Set.copyOf(scopes));
            }
        }
    }
}
