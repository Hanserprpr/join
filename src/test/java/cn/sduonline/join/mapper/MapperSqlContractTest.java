package cn.sduonline.join.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.sduonline.join.data.po.DepartmentInterviewSession;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class MapperSqlContractTest {

    @Test
    void interviewSessionPersistsAndLoadsQrConfiguration()
            throws NoSuchMethodException {
        Method insert = DepartmentInterviewSessionMapper.class.getMethod(
                "insert", DepartmentInterviewSession.class
        );
        Method select = DepartmentInterviewSessionMapper.class.getMethod(
                "selectPublished", Long.class
        );

        String insertSql = sql(insert.getAnnotation(Insert.class).value());
        String selectSql = sql(select.getAnnotation(Select.class).value());
        assertTrue(insertSql.contains("qr_check_in_enabled"));
        assertTrue(insertSql.contains("qr_code_ttl_seconds"));
        assertTrue(insertSql.contains("name"));
        assertTrue(selectSql.contains("qr_check_in_enabled"));
        assertTrue(selectSql.contains("qr_code_ttl_seconds"));
        assertTrue(selectSql.contains("name"));
    }

    @Test
    void publishingDoesNotRejectAnotherPublishedSession()
            throws NoSuchMethodException {
        Method method = DepartmentInterviewSessionMapper.class.getMethod(
                "publish", Long.class, Long.class,
                java.time.LocalDateTime.class
        );

        assertFalse(sql(method.getAnnotation(Update.class).value())
                .contains("NOT EXISTS"));
    }

    @Test
    void pendingCarryoverCanBeClaimedByTheChosenSession()
            throws NoSuchMethodException {
        Method method = DepartmentInterviewSessionMapper.class.getMethod(
                "selectPendingCarryoverForUpdate",
                Long.class, Long.class, Long.class
        );

        assertTrue(sql(method.getAnnotation(Select.class).value())
                .contains("target_session_id IS NULL"));
    }

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
    void queueReorderingPersistsPriorityChanges()
            throws NoSuchMethodException {
        Method method = DepartmentInterviewMapper.class.getMethod(
                "updateCheckInQueue",
                cn.sduonline.join.data.po.DepartmentCheckIn.class
        );

        assertTrue(sql(method.getAnnotation(Update.class).value())
                .contains("priority = #{priority}"));
    }

    @Test
    void queueReorderingIsScopedToOneSession()
            throws NoSuchMethodException {
        Method method = DepartmentInterviewMapper.class.getMethod(
                "selectReorderableQueueForUpdate", Long.class, Long.class
        );

        assertTrue(sql(method.getAnnotation(Select.class).value())
                .contains("c.session_id = #{sessionId}"));
    }

    @Test
    void applicantQueriesExposeCompletedInterviews()
            throws NoSuchMethodException {
        Method one = DepartmentApplicationMapper.class.getMethod(
                "selectByDepartmentAndUser", Long.class, String.class
        );
        Method all = DepartmentApplicationMapper.class.getMethod(
                "selectByUser", String.class
        );

        for (Method method : List.of(one, all)) {
            String sql = sql(method.getAnnotation(Select.class).value());
            assertTrue(sql.contains("i.ended_at IS NOT NULL"));
            assertTrue(sql.contains("AS interviewed"));
        }
    }

    @Test
    void queueItemQueriesMatchTheRecordConstructor()
            throws NoSuchMethodException {
        Method queue = DepartmentInterviewMapper.class.getMethod(
                "selectQueue", Long.class, Long.class
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
                    .contains(
                            "own_interview.ended_at, c.priority, c.session_id"
                    ));
            assertTrue(sql.contains("LEFT JOIN `user` interviewer"));
        }
    }

    @Test
    void publicQueueExcludesCompletedButPersonalStatusKeepsIt()
            throws NoSuchMethodException {
        Method queue = DepartmentInterviewMapper.class.getMethod(
                "selectQueue", Long.class, Long.class
        );
        Method candidate = DepartmentInterviewMapper.class.getMethod(
                "selectCandidateQueueItem", Long.class, String.class
        );

        assertTrue(sql(queue.getAnnotation(Select.class).value())
                .contains("AND own_interview.ended_at IS NULL"));
        assertFalse(sql(candidate.getAnnotation(Select.class).value())
                .contains("AND own_interview.ended_at IS NULL"));
    }

    @Test
    void interviewingAndQueueReadsDoNotDependOnSessionStatus()
            throws NoSuchMethodException {
        // 场次结束只关签到：排队的人还要继续面完，因此叫号、重排、队列展示、
        // 个人状态都不能再挂在 status = 'PUBLISHED' 上。
        List<Method> methods = List.of(
                DepartmentInterviewMapper.class.getMethod(
                        "selectNextWaitingForUpdate", Long.class, Long.class),
                DepartmentInterviewMapper.class.getMethod(
                        "selectReorderableQueueForUpdate",
                        Long.class, Long.class),
                DepartmentInterviewMapper.class.getMethod(
                        "selectQueue", Long.class, Long.class),
                DepartmentInterviewMapper.class.getMethod(
                        "selectCandidateQueueItem", Long.class, String.class),
                DepartmentInterviewMapper.class.getMethod(
                        "countPeopleAhead",
                        Long.class, Long.class, Long.class, Boolean.class),
                DepartmentInterviewMapper.class.getMethod(
                        "selectInterviewingQueueNumbers",
                        Long.class, Long.class),
                DepartmentCheckInMapper.class.getMethod(
                        "selectCurrentByDepartmentAndUserForUpdate",
                        Long.class, Long.class, String.class)
        );

        for (Method method : methods) {
            assertFalse(
                    sql(method.getAnnotation(Select.class).value())
                            .contains("s.status = 'PUBLISHED'"),
                    method.getName() + " 不应再按场次状态过滤"
            );
        }
    }

    @Test
    void dispatchSkipsAnyoneAlreadyInterviewedInTheDepartment()
            throws NoSuchMethodException {
        // 去掉 PUBLISHED 过滤后，同一个人可能同时留在已结束场次和进行中场次的
        // 队列里。排除条件必须按"本部门是否已面过"算，只按 check_in_id 算会
        // 让他被叫第二次。
        Method method = DepartmentInterviewMapper.class.getMethod(
                "selectNextWaitingForUpdate", Long.class, Long.class
        );
        String sql = sql(method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("done.candidate_cas_id = c.cas_id"));
        assertTrue(sql.contains("done.department_id = c.department_id"));
        assertTrue(sql.contains("done.id IS NULL"));
        assertFalse(sql.contains("i.check_in_id = c.id"));
    }

    @Test
    void candidateQueueItemPicksTheMostRecentCheckIn()
            throws NoSuchMethodException {
        // 去掉 PUBLISHED 过滤后，历史场次会给同一个人留下多条签到，
        // 必须显式收敛到最近一条，否则会撞 TooManyResultsException。
        Method method = DepartmentInterviewMapper.class.getMethod(
                "selectCandidateQueueItem", Long.class, String.class
        );
        String sql = sql(method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("ORDER BY c.checked_in_at DESC, c.id DESC"));
        assertTrue(sql.contains("LIMIT 1"));
    }

    @Test
    void carryoverCancellationOnlyTouchesThisSessionsAttendees()
            throws NoSuchMethodException {
        // 顺延只对下一场有效，但作废必须限定在本场到场的人身上：按整个部门清
        // 会把并行场次（另一校区）里还没签到的考生的资格提前作废。
        Method method = DepartmentInterviewSessionMapper.class.getMethod(
                "cancelUnusedCarryovers", Long.class, Long.class
        );
        String sql = sql(method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("status = 'PENDING'"));
        assertTrue(sql.contains("department_id = #{departmentId}"));
        assertTrue(sql.contains("c.session_id = #{sessionId}"));
        assertFalse(sql.contains("target_session_id = #{sessionId}"));
    }

    @Test
    void queueReadsAreScopedToASingleSession()
            throws NoSuchMethodException {
        Method queue = DepartmentInterviewMapper.class.getMethod(
                "selectQueue", Long.class, Long.class
        );
        Method interviewing = DepartmentInterviewMapper.class.getMethod(
                "selectInterviewingQueueNumbers", Long.class, Long.class
        );
        Method ahead = DepartmentInterviewMapper.class.getMethod(
                "countPeopleAhead",
                Long.class, Long.class, Long.class, Boolean.class
        );

        assertTrue(sql(queue.getAnnotation(Select.class).value())
                .contains("c.session_id = #{sessionId}"));
        assertTrue(sql(interviewing.getAnnotation(Select.class).value())
                .contains("c.session_id = #{sessionId}"));
        assertTrue(sql(ahead.getAnnotation(Select.class).value())
                .contains("ahead.session_id = #{sessionId}"));
    }

    @Test
    void checkInRejectsASecondQueueInAnotherPublishedSession()
            throws NoSuchMethodException {
        Method method = DepartmentCheckInMapper.class.getMethod(
                "countOtherSessionCheckIns",
                Long.class, Long.class, Long.class
        );
        String sql = sql(method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("c.department_id = #{departmentId}"));
        assertTrue(sql.contains("c.application_id = #{applicationId}"));
        assertTrue(sql.contains("c.session_id <> #{sessionId}"));
        assertTrue(sql.contains("s.status = 'PUBLISHED'"));
    }

    @Test
    void cancellingCheckInLocksAndDeletesOnlyOwnedCurrentRecord()
            throws NoSuchMethodException {
        Method select = DepartmentCheckInMapper.class.getMethod(
                "selectCurrentByDepartmentAndUserForUpdate",
                Long.class, Long.class, String.class
        );
        Method delete = DepartmentCheckInMapper.class.getMethod(
                "deleteOwnedCheckIn", Long.class, Long.class, String.class
        );
        String selectSql = sql(select.getAnnotation(Select.class).value());
        String deleteSql = sql(delete.getAnnotation(
                org.apache.ibatis.annotations.Delete.class).value());

        assertTrue(selectSql.contains("c.session_id = #{sessionId}"));
        assertTrue(selectSql.contains("FOR UPDATE"));
        assertTrue(deleteSql.contains("cas_id = #{casId}"));
        assertTrue(deleteSql.contains("department_id = #{departmentId}"));
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

    @Test
    void roleAssignmentMemberLookupKeepsInheritedAndGlobalAssignments()
            throws NoSuchMethodException {
        Method members = AdminRoleAssignmentMapper.class.getMethod(
                "selectMembersByScope", String.class, Long.class
        );
        Method search = AdminRoleAssignmentMapper.class.getMethod(
                "selectUsersByCasIdKeyword", String.class
        );
        Method tree = AdminOrganizationMapper.class.getMethod(
                "selectEnabledOrganizationTree"
        );
        String memberSql = sql(members.getAnnotation(Select.class).value());
        String searchSql = sql(search.getAnnotation(Select.class).value());
        String treeSql = sql(tree.getAnnotation(Select.class).value());

        assertTrue(memberSql.contains("s.scope_type = 'ALL'"));
        assertTrue(memberSql.contains("#{targetScopeType} = 'DEPARTMENT'"));
        assertTrue(memberSql.contains("JOIN workstation w ON w.id = d.workstation_id"));
        assertTrue(treeSql.contains("WHERE b.enabled = 1"));
        assertFalse(treeSql.contains("user_role_scope"));
        assertTrue(treeSql.contains("d.campus AS department_campuses"));
        assertTrue(treeSql.contains("d.asset_id AS department_asset_id"));
        assertTrue(treeSql.contains("d.introduction AS department_introduction"));
        assertFalse(treeSql.contains("d.contact"));
        assertFalse(treeSql.contains("d.recruitment_group"));
        assertFalse(treeSql.contains("d.recruitment_requirements"));
        assertTrue(searchSql.contains("ESCAPE '!'"));
        assertTrue(searchSql.contains("CONCAT('%', #{casIdKeyword}, '%')"));
        assertTrue(searchSql.contains("LIMIT 20"));
    }

    @Test
    void wechatUnbindExplicitlyWritesNull() throws NoSuchMethodException {
        Method method = UserMapper.class.getMethod("clearWechatOpenid", String.class);
        String updateSql = sql(method.getAnnotation(Update.class).value());

        assertTrue(updateSql.contains("wechat_openid = NULL"));
        assertTrue(updateSql.contains("WHERE cas_id = #{casId}"));
    }

    @Test
    void departmentAchievementsReadAndWriteImageUrls()
            throws NoSuchMethodException {
        Method select = AdminOrganizationMapper.class.getMethod(
                "selectDepartmentAchievements", Long.class
        );
        Method insert = AdminOrganizationMapper.class.getMethod(
                "insertDepartmentAchievement",
                cn.sduonline.join.data.po.DepartmentAchievement.class
        );

        String selectSql = sql(select.getAnnotation(Select.class).value());
        String insertSql = sql(insert.getAnnotation(Insert.class).value());

        assertTrue(selectSql.contains("image_urls"));
        assertTrue(insertSql.contains("image_urls"));
        assertTrue(insertSql.contains("#{imageUrls,typeHandler="));
    }

    @Test
    void posterReorderingLocksDepartmentAndPostersAndScopesEveryUpdate()
            throws NoSuchMethodException {
        Method departmentLock = AdminOrganizationMapper.class.getMethod(
                "selectDepartmentByIdForUpdate", Long.class
        );
        Method posterLock = AdminOrganizationMapper.class.getMethod(
                "selectDepartmentPostersForUpdate", Long.class
        );
        Method update = AdminOrganizationMapper.class.getMethod(
                "updateDepartmentPosterSortOrder",
                Long.class, Long.class, Integer.class
        );

        String departmentLockSql = sql(
                departmentLock.getAnnotation(Select.class).value()
        );
        String posterLockSql = sql(
                posterLock.getAnnotation(Select.class).value()
        );
        String updateSql = sql(update.getAnnotation(Update.class).value());

        assertTrue(departmentLockSql.contains("WHERE id = #{id}"));
        assertTrue(departmentLockSql.contains("FOR UPDATE"));
        assertTrue(posterLockSql.contains(
                "WHERE department_id = #{departmentId}"
        ));
        assertTrue(posterLockSql.contains("ORDER BY id ASC"));
        assertTrue(posterLockSql.contains("FOR UPDATE"));
        assertTrue(updateSql.contains("SET sort_order = #{sortOrder}"));
        assertTrue(updateSql.contains(
                "WHERE department_id = #{departmentId}"
        ));
        assertTrue(updateSql.contains("AND id = #{posterId}"));
    }

    private static String sql(String[] fragments) {
        return String.join("\n", fragments);
    }
}
