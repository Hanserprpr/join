package cn.sduonline.join.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.sduonline.join.data.po.DepartmentInterviewRoom;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

class InterviewRoomNameQueryTest {

    @Test
    void migrationAllowsRepeatedNameReuseAfterClosingAndPreservesHistory() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:room-names-" + UUID.randomUUID()
                + ";MODE=MySQL");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE department_interview_room (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        department_id BIGINT NOT NULL, session_id BIGINT NOT NULL,
                        name VARCHAR(64) NOT NULL, status VARCHAR(16) NOT NULL,
                        created_by VARCHAR(32), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_interview_room_session_name (session_id, name))
                    """);
            statement.execute("""
                    INSERT INTO department_interview_room
                        (department_id, session_id, name, status) VALUES
                        (12, 5, '第一面试室', 'CLOSED'),
                        (12, 6, '第一面试室', 'OPEN')
                    """);
            try (var migration = new InputStreamReader(Objects.requireNonNull(
                    getClass().getResourceAsStream(
                            "/db/migration/V17__allow_reusing_closed_interview_room_names.sql"
                    )), StandardCharsets.UTF_8)) {
                RunScript.execute(connection, migration);
            }
            Configuration configuration = new Configuration(new Environment(
                    "test", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(DepartmentInterviewRoomMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(configuration);

            try (var session = factory.openSession()) {
                var mapper = session.getMapper(DepartmentInterviewRoomMapper.class);
                assertNull(mapper.selectOpenIdBySessionAndNameForUpdate(5L, "第一面试室"));
                assertEquals(2L, mapper.selectOpenIdBySessionAndNameForUpdate(6L, "第一面试室"));
                assertNull(mapper.selectOpenIdBySessionAndNameForUpdate(7L, "第一面试室"));

                for (int i = 0; i < 2; i++) {
                    var room = room(5L);
                    assertEquals(1, mapper.insert(room));
                    assertNotEquals(1L, room.getId());
                    assertEquals(room.getId(), mapper.selectOpenIdBySessionAndNameForUpdate(
                            5L, "第一面试室"));
                    PersistenceException duplicate = assertThrows(PersistenceException.class,
                            () -> mapper.insert(room(5L)));
                    assertEquals("23505", ((java.sql.SQLException) duplicate.getCause()).getSQLState());
                    assertEquals(1, mapper.close(12L, room.getId()));
                    assertNull(mapper.selectOpenIdBySessionAndNameForUpdate(5L, "第一面试室"));
                    session.commit();
                }

                // 旧房间的 ID、原名称和关闭状态保持不变，历史记录仍可按 ID 关联。
                var original = mapper.selectById(12L, 1L);
                assertEquals("第一面试室", original.getName());
                assertEquals("CLOSED", original.getStatus());
                assertEquals(3, mapper.selectBySession(12L, 5L).size());
                assertEquals(2L, mapper.selectOpenIdBySessionAndNameForUpdate(6L, "第一面试室"));
            }
        }
    }

    private static DepartmentInterviewRoom room(Long sessionId) {
        var room = new DepartmentInterviewRoom();
        room.setDepartmentId(12L);
        room.setSessionId(sessionId);
        room.setName("第一面试室");
        room.setCreatedBy("admin01");
        return room;
    }
}
