package cn.sduonline.join.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.sduonline.join.data.dto.StudentAcademicProfile;
import java.sql.DriverManager;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class StudentAcademicProfileMapperTest {

    @Test
    void resolvesCasAccountThroughInternalIdAndOnlyReturnsAcademicFields() throws Exception {
        String url = "jdbc:h2:mem:academic-profile-" + UUID.randomUUID() + ";MODE=MySQL";
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA isdusvr_db");
            statement.execute("""
                    CREATE TABLE isdusvr_db.isdu_basic_cas (
                        id INT PRIMARY KEY, cas_id VARCHAR(32) UNIQUE)
                    """);
            statement.execute("""
                    CREATE TABLE isdusvr_db.isdu_basic_user (
                        id INT PRIMARY KEY, depart VARCHAR(255), major VARCHAR(255))
                    """);
            statement.execute("""
                    INSERT INTO isdusvr_db.isdu_basic_cas VALUES
                        (42, '20240001'), (73, '20240002'), (99, '20240003'), (101, '20240004')
                    """);
            statement.execute("""
                    INSERT INTO isdusvr_db.isdu_basic_user VALUES
                        (42, '软件学院', '软件工程'), (73, '药学院', '药学'),
                        (101, '软件学院', NULL), (20240001, '错误学院', '错误专业')
                    """);

            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL(url);
            Configuration configuration = new Configuration(new Environment(
                    "test", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(StudentAcademicProfileMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
                var mapper = session.getMapper(StudentAcademicProfileMapper.class);
                assertEquals(new StudentAcademicProfile("软件学院", "软件工程"),
                        mapper.selectByCasId("20240001"));
                assertEquals(new StudentAcademicProfile("药学院", "药学"),
                        mapper.selectByCasId("20240002"));
                assertNull(mapper.selectByCasId("missing"));
                assertNull(mapper.selectByCasId("20240003"));
                assertEquals(new StudentAcademicProfile("软件学院", null),
                        mapper.selectByCasId("20240004"));
                assertNull(mapper.selectByCasId("20240001' OR '1' = '1"));
            }
        }
    }
}
