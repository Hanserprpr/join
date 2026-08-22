package cn.sduonline.join.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class MapperSqlContractTest {

    @Test
    void interviewLookupMapsRoomIdUsedByAuthorizationChecks()
            throws NoSuchMethodException {
        Method method = DepartmentInterviewMapper.class.getMethod(
                "selectByIdAndDepartment", Long.class, Long.class
        );

        assertTrue(sql(method.getAnnotation(Select.class).value())
                .contains("i.room_id"));
    }

    @Test
    void queueClaimIsScopedToTheRoomsSession()
            throws NoSuchMethodException {
        Method method = DepartmentInterviewMapper.class.getMethod(
                "selectNextWaitingForUpdate", Long.class, Long.class
        );
        String sql = sql(method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("c.session_id = #{sessionId}"));
        assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"));
    }

    @Test
    void queueItemQueriesMatchTheRecordConstructor()
            throws NoSuchMethodException {
        Method queue = DepartmentInterviewMapper.class.getMethod(
                "selectQueue", Long.class
        );
        Method candidate = DepartmentInterviewMapper.class.getMethod(
                "selectCandidateQueueItem", Long.class, String.class
        );

        for (Method method : List.of(queue, candidate)) {
            String sql = sql(method.getAnnotation(Select.class).value());
            assertTrue(sql.contains("AS status,"));
            assertTrue(sql.contains("AS interviewer_cas_id,"));
            assertTrue(sql.contains("AS interviewer_name,"));
            assertTrue(sql.replaceAll("\\s+", " ")
                    .contains("own_interview.ended_at, c.priority"));
            assertTrue(sql.contains("LEFT JOIN `user` interviewer"));
        }
    }

    @Test
    void admissionPublishingLocksAndUpdatesTheSameIdentifiers()
            throws NoSuchMethodException {
        Method mutate = DepartmentApplicationMapper.class.getMethod(
                "selectByDepartmentAndIdForUpdate", Long.class, Long.class
        );
        Method select = DepartmentApplicationMapper.class.getMethod(
                "selectAdmissionDraftsForUpdate", Long.class
        );
        Method update = DepartmentApplicationMapper.class.getMethod(
                "publishAdmissionDraftsByIds", Long.class, List.class
        );

        assertTrue(sql(mutate.getAnnotation(Select.class).value())
                .contains("FOR UPDATE"));
        assertTrue(sql(select.getAnnotation(Select.class).value())
                .contains("FOR UPDATE"));
        assertTrue(sql(update.getAnnotation(Update.class).value())
                .contains("collection=\"applicationIds\""));
    }

    @Test
    void departmentRoleAccessExcludesRolesWithoutAnyPermission()
            throws NoSuchMethodException {
        Method method = AuthorizationMapper.class.getMethod(
                "selectDepartmentRoleAccess", String.class
        );
        String sql = sql(method.getAnnotation(Select.class).value());

        // COALESCE(p.code, '*') 只应该给 SYSTEM_ADMIN 兜底；没有配置任何权限的
        // 角色必须整行剔除，否则会被误报成该部门的 * 全权限。
        assertTrue(sql.contains("r.code = 'SYSTEM_ADMIN' OR p.code IS NOT NULL"));
    }

    @Test
    void organizationScopeChecksRequireAnEnabledTarget()
            throws NoSuchMethodException {
        Method board = AuthorizationMapper.class.getMethod(
                "countBoardPermissionAccess", String.class, String.class, Long.class
        );
        Method workstation = AuthorizationMapper.class.getMethod(
                "countWorkstationPermissionAccess",
                String.class, String.class, Long.class
        );
        Method department = AuthorizationMapper.class.getMethod(
                "countDepartmentPermissionAccess",
                String.class, String.class, Long.class
        );

        assertTrue(sql(board.getAnnotation(Select.class).value())
                .contains("b.id = #{boardId} AND b.enabled = 1"));
        assertTrue(sql(workstation.getAnnotation(Select.class).value())
                .contains("w.id = #{workstationId} AND w.enabled = 1"));
        assertTrue(sql(department.getAnnotation(Select.class).value())
                .contains("dt.id = #{departmentId} AND dt.enabled = 1"));
    }

    private static String sql(String[] fragments) {
        return String.join("\n", fragments);
    }
}
