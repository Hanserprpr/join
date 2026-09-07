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
    void aheadCandidatesFollowPriorityAndExcludeOtherSessionsAndUnavailablePeople()
            throws Exception {
        String url = "jdbc:h2:mem:ahead-" + java.util.UUID.randomUUID()
                + ";MODE=MySQL";
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE department_check_in (
                        id BIGINT PRIMARY KEY, department_id BIGINT,
                        session_id BIGINT, cas_id VARCHAR(32),
                        queue_number INT, queue_order BIGINT,
                        priority BOOLEAN, requires_recheck_in BOOLEAN)
                    """);
            statement.execute("""
                    CREATE TABLE `user` (cas_id VARCHAR(32), name VARCHAR(64))
                    """);
            statement.execute("""
                    CREATE TABLE department_interview (
                        id BIGINT PRIMARY KEY, check_in_id BIGINT, ended_at TIMESTAMP)
                    """);
            statement.execute("""
                    CREATE TABLE department_interview_active (
                        interview_id BIGINT, candidate_cas_id VARCHAR(32))
                    """);
            statement.execute("""
                    INSERT INTO department_check_in VALUES
                        (1, 12, 5, 'a', 1, 1, FALSE, FALSE),
                        (2, 12, 5, 'b', 2, 20, TRUE, FALSE),
                        (3, 12, 6, 'c', 3, 2, FALSE, FALSE),
                        (4, 13, 5, 'd', 4, 3, FALSE, FALSE),
                        (5, 12, 5, 'e', 5, 4, FALSE, TRUE),
                        (6, 12, 5, 'f', 6, 5, FALSE, FALSE),
                        (7, 12, 5, 'g', 7, 6, FALSE, FALSE),
                        (8, 12, 5, 'h', 8, 7, FALSE, FALSE),
                        (9, 12, 5, 'self', 9, 10, FALSE, FALSE),
                        (10, 12, 5, 'after', 10, 11, FALSE, FALSE)
                    """);
            statement.execute("""
                    INSERT INTO `user` VALUES ('a', '张三'), ('b', '李四'), ('h', '王五')
                    """);
            statement.execute("""
                    INSERT INTO department_interview VALUES
                        (6, 6, CURRENT_TIMESTAMP), (8, 8, NULL)
                    """);
            statement.execute("""
                    INSERT INTO department_interview_active VALUES (70, 'g'), (8, 'h')
                    """);
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL(url);
            Configuration configuration = new Configuration(new Environment(
                    "test", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(DepartmentInterviewMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(configuration);
            try (var session = factory.openSession()) {
                var mapper = session.getMapper(DepartmentInterviewMapper.class);
                var candidates = mapper.selectPeopleAheadCandidates(12L, 5L, 10L, false);
                assertEquals(java.util.List.of(2, 1, 8), candidates.stream()
                        .map(c -> c.queueNumber()).toList());
                assertEquals(java.util.List.of("李四", "张三", "王五"), candidates.stream()
                        .map(c -> c.candidateName()).toList());
                assertEquals(java.util.List.of(2), mapper
                        .selectPeopleAheadCandidates(12L, 5L, 21L, true).stream()
                        .map(c -> c.queueNumber()).toList());
                assertEquals(java.util.List.of(), mapper
                        .selectPeopleAheadCandidates(12L, 5L, 20L, true));
            }
        }
    }

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
