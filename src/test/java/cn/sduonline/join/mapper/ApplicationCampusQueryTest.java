package cn.sduonline.join.mapper;

import static org.junit.jupiter.api.Assertions.*;

import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.enums.ApplicationStatus;
import java.sql.DriverManager;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class ApplicationCampusQueryTest {
    @Test
    void filtersBeforePaginationAndKeepsLegacyNullCampusWithoutFilter() throws Exception {
        String url = "jdbc:h2:mem:campus-" + java.util.UUID.randomUUID() + ";MODE=MySQL";
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `user` (cas_id VARCHAR(32), name VARCHAR(64),
                      college VARCHAR(64), major VARCHAR(64), grade INT,
                      phone VARCHAR(20), email VARCHAR(128), qq VARCHAR(20), campus VARCHAR(32))
                    """);
            statement.execute("""
                    CREATE TABLE department_application (id BIGINT, department_id BIGINT,
                      cas_id VARCHAR(32), status VARCHAR(32), submitted_at TIMESTAMP)
                    """);
            statement.execute("CREATE TABLE department_interview (id BIGINT, application_id BIGINT, ended_at TIMESTAMP)");
            statement.execute("CREATE TABLE department_interview_evaluation (interview_id BIGINT, score DECIMAL(3,1))");
            statement.execute("""
                    INSERT INTO `user` VALUES
                      ('a','Alice','College','Major',2026,'1',NULL,NULL,'CENTRAL'),
                      ('b','Bob','College','Major',2026,'2',NULL,NULL,'SOFTWARE_PARK'),
                      ('c','Carol','College','Major',2026,'3',NULL,NULL,NULL),
                      ('d','Dan','College','Major',2026,'4',NULL,NULL,'CENTRAL')
                    """);
            statement.execute("""
                    INSERT INTO department_application VALUES
                      (1,12,'a','SUBMITTED',CURRENT_TIMESTAMP),
                      (2,12,'b','SUBMITTED',CURRENT_TIMESTAMP),
                      (3,12,'c','SUBMITTED',CURRENT_TIMESTAMP),
                      (4,12,'d','SUBMITTED',CURRENT_TIMESTAMP),
                      (5,13,'a','SUBMITTED',CURRENT_TIMESTAMP)
                    """);
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL(url);
            Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
            config.setMapUnderscoreToCamelCase(true);
            config.addMapper(DepartmentApplicationMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
                var mapper = session.getMapper(DepartmentApplicationMapper.class);
                assertEquals(4, mapper.countApplications(12L, null, null, null, null, null, null));
                var all = mapper.selectApplications(12L, null, null, null, null, null, "submittedAt", "desc", 0, 20, null);
                assertEquals(4, all.size());
                assertNull(all.stream().filter(a -> a.getId() == 3L).findFirst().orElseThrow().getCampus());
                assertEquals(2, mapper.countApplications(12L, null, null, null, null, null, Campus.CENTRAL));
                var page = mapper.selectApplications(12L, null, null, null, null, null, "submittedAt", "desc", 1, 1, Campus.CENTRAL);
                assertEquals(1, page.size());
                assertEquals(1L, page.getFirst().getId());
                assertEquals(Campus.CENTRAL, page.getFirst().getCampus());
                var export = mapper.selectApplications(12L, null, null, null, null, null, null, null, null, null, Campus.CENTRAL);
                assertEquals(2, export.size());
                assertEquals(1, mapper.countApplications(12L, "Alice", "College", 2026, false, ApplicationStatus.SUBMITTED, Campus.CENTRAL));
                assertEquals(0, mapper.countApplications(12L, null, null, null, null, null, Campus.BAOTUQUAN));
                assertEquals(Campus.CENTRAL, mapper.selectApplicationDetail(12L, 1L).getCampus());
            }
        }
    }
}
