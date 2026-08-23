package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.OrganizationTreeRow;
import cn.sduonline.join.data.dto.OrganizationTreeVO;
import cn.sduonline.join.data.dto.RoleAssignmentRequest;
import cn.sduonline.join.data.dto.RoleAssignmentMemberVO;
import cn.sduonline.join.data.dto.RoleAssignmentVO;
import cn.sduonline.join.data.dto.UserSearchVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.UserRoleScope;
import cn.sduonline.join.mapper.AdminRoleAssignmentMapper;
import cn.sduonline.join.security.scope.OrgType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminRoleAssignmentServiceTest {

    @Mock
    private AdminRoleAssignmentMapper assignmentMapper;

    private AdminRoleAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new AdminRoleAssignmentService(assignmentMapper);
        lenient().when(assignmentMapper.countAdminRole(anyString())).thenReturn(1L);
    }

    @Test
    void organizationTreeContainsDepartmentsBelowEachWorkstation() {
        when(assignmentMapper.countRoleAssignmentManager("operator-01"))
                .thenReturn(1L);
        when(assignmentMapper.selectEnabledOrganizationTree("operator-01")).thenReturn(List.of(
                new OrganizationTreeRow(
                        1L, "技术板块", 5L, "开发工作站", 12L, "后端部门"
                ),
                new OrganizationTreeRow(
                        1L, "技术板块", 5L, "开发工作站", 13L, "前端部门"
                ),
                new OrganizationTreeRow(
                        2L, "运营板块", null, null, null, null
                )
        ));

        ServiceResult<List<OrganizationTreeVO>> result =
                service.findOrganizationTree("operator-01");

        assertTrue(result.isSuccess());
        assertEquals(2, result.data().size());
        assertEquals("技术板块", result.data().getFirst().name());
        assertEquals(1, result.data().getFirst().workstations().size());
        assertEquals(2, result.data().getFirst().workstations()
                .getFirst().departments().size());
        assertEquals("后端部门", result.data().getFirst().workstations()
                .getFirst().departments().getFirst().name());
        assertTrue(result.data().get(1).workstations().isEmpty());
    }

    @Test
    void organizationTreeRejectsAssistantOnlyOperator() {
        when(assignmentMapper.countRoleAssignmentManager("operator-01"))
                .thenReturn(0L);

        ServiceResult<List<OrganizationTreeVO>> result =
                service.findOrganizationTree("operator-01");

        assertEquals(BizCode.NO_PERMISSION, result.error());
        verify(assignmentMapper, never()).selectEnabledOrganizationTree(anyString());
    }

    @Test
    void memberLookupUsesScopeCoverageAndReturnsOriginalAssignments() {
        RoleAssignmentMemberVO member = new RoleAssignmentMemberVO(
                8L, "20240001", "张三", "BOARD_ADMIN", "板块管理员",
                "BOARD", 1L, "技术板块", 1L, "技术板块",
                null, null, null, null
        );
        when(assignmentMapper.countRoleAssignmentManager("operator-01"))
                .thenReturn(1L);
        when(assignmentMapper.countEnabledDepartment(12L)).thenReturn(1L);
        when(assignmentMapper.countScopeCoverage(
                "operator-01", "DEPARTMENT", 12L
        )).thenReturn(1L);
        when(assignmentMapper.selectMembersByScope("DEPARTMENT", 12L))
                .thenReturn(List.of(member));

        ServiceResult<List<RoleAssignmentMemberVO>> result = service.findMembers(
                "operator-01", OrgType.DEPARTMENT, 12L
        );

        assertTrue(result.isSuccess());
        assertEquals(member, result.data().getFirst());
    }

    @Test
    void memberLookupRejectsOrganizationOutsideOperatorsScope() {
        when(assignmentMapper.countRoleAssignmentManager("operator-01"))
                .thenReturn(1L);
        when(assignmentMapper.countEnabledWorkstation(5L)).thenReturn(1L);
        when(assignmentMapper.countScopeCoverage(
                "operator-01", "WORKSTATION", 5L
        )).thenReturn(0L);

        ServiceResult<List<RoleAssignmentMemberVO>> result = service.findMembers(
                "operator-01", OrgType.WORKSTATION, 5L
        );

        assertEquals(BizCode.NO_PERMISSION, result.error());
        verify(assignmentMapper, never()).selectMembersByScope(anyString(), any());
    }

    @Test
    void userSearchEscapesLikeWildcardsAndSkipsShortInput() {
        when(assignmentMapper.countRoleAssignmentManager("operator-01"))
                .thenReturn(1L);
        when(assignmentMapper.selectUsersByCasIdKeyword("2024!_0"))
                .thenReturn(List.of(new UserSearchVO("2024_01", "张三")));

        ServiceResult<List<UserSearchVO>> matched = service.searchUsers(
                "operator-01", "2024_0"
        );
        ServiceResult<List<UserSearchVO>> blank = service.searchUsers(
                "operator-01", "  "
        );
        ServiceResult<List<UserSearchVO>> shortKeyword = service.searchUsers(
                "operator-01", "20240"
        );

        assertTrue(matched.isSuccess());
        assertEquals("2024_01", matched.data().getFirst().casId());
        assertTrue(blank.isSuccess());
        assertTrue(blank.data().isEmpty());
        assertTrue(shortKeyword.isSuccess());
        assertTrue(shortKeyword.data().isEmpty());
        verify(assignmentMapper).selectUsersByCasIdKeyword("2024!_0");
    }

    @Test
    void assignCreatesWorkstationManager() {
        RoleAssignmentRequest request = request(
                "WORKSTATION_ADMIN", OrgType.WORKSTATION, 5L
        );
        when(assignmentMapper.countUser("20240001")).thenReturn(1L);
        when(assignmentMapper.countEnabledWorkstation(5L)).thenReturn(1L);
        when(assignmentMapper.countGrantAuthority(
                "operator-01", 30, "WORKSTATION", 5L
        )).thenReturn(1L);
        when(assignmentMapper.selectRoleId("WORKSTATION_ADMIN")).thenReturn(2L);
        when(assignmentMapper.countAssignment(
                "20240001", 2L, "WORKSTATION", 5L
        )).thenReturn(0L);
        when(assignmentMapper.insertAssignment(any())).thenAnswer(invocation -> {
            UserRoleScope assignment = invocation.getArgument(0);
            assignment.setId(8L);
            return 1;
        });

        ServiceResult<RoleAssignmentVO> result = service.assign("operator-01", request);

        assertTrue(result.isSuccess());
        assertEquals(8L, result.data().id());
        assertEquals("WORKSTATION_ADMIN", result.data().roleCode());
    }

    @Test
    void assignRejectsMismatchedScope() {
        when(assignmentMapper.countUser("20240001")).thenReturn(1L);

        ServiceResult<RoleAssignmentVO> result = service.assign(
                "operator-01",
                request("BOARD_ADMIN", OrgType.DEPARTMENT, 12L)
        );

        assertEquals(BizCode.ROLE_SCOPE_MISMATCH, result.error());
        verify(assignmentMapper, never()).insertAssignment(any());
    }

    @Test
    void assignRejectsDuplicateAssignment() {
        when(assignmentMapper.countUser("20240001")).thenReturn(1L);
        when(assignmentMapper.countEnabledDepartment(12L)).thenReturn(1L);
        when(assignmentMapper.countGrantAuthority(
                "operator-01", 20, "DEPARTMENT", 12L
        )).thenReturn(1L);
        when(assignmentMapper.selectRoleId("DEPARTMENT_ADMIN")).thenReturn(3L);
        when(assignmentMapper.countAssignment(
                "20240001", 3L, "DEPARTMENT", 12L
        )).thenReturn(1L);

        ServiceResult<RoleAssignmentVO> result = service.assign(
                "operator-01",
                request("DEPARTMENT_ADMIN", OrgType.DEPARTMENT, 12L)
        );

        assertEquals(BizCode.ROLE_ASSIGNMENT_EXISTS, result.error());
        verify(assignmentMapper, never()).insertAssignment(any());
    }

    @Test
    void assignRejectsSameOrHigherRoleOrOutOfScope() {
        when(assignmentMapper.countUser("20240001")).thenReturn(1L);
        when(assignmentMapper.countEnabledWorkstation(5L)).thenReturn(1L);
        when(assignmentMapper.countGrantAuthority(
                "operator-01", 30, "WORKSTATION", 5L
        )).thenReturn(0L);

        ServiceResult<RoleAssignmentVO> result = service.assign(
                "operator-01",
                request("WORKSTATION_ADMIN", OrgType.WORKSTATION, 5L)
        );

        assertEquals(BizCode.ROLE_ASSIGNMENT_FORBIDDEN, result.error());
        verify(assignmentMapper, never()).insertAssignment(any());
    }

    @Test
    void assignRejectsNonAdministratorBeforeCheckingTargetUser() {
        when(assignmentMapper.countAdminRole("operator-01")).thenReturn(0L);

        ServiceResult<RoleAssignmentVO> result = service.assign(
                "operator-01",
                request("DEPARTMENT_ASSISTANT", OrgType.DEPARTMENT, 12L)
        );

        assertEquals(BizCode.ROLE_ASSIGNMENT_FORBIDDEN, result.error());
        verify(assignmentMapper, never()).countUser(anyString());
        verify(assignmentMapper, never()).insertAssignment(any());
    }

    @Test
    void departmentAdminCanAssignAssistantInOwnDepartment() {
        when(assignmentMapper.countUser("20240001")).thenReturn(1L);
        when(assignmentMapper.countEnabledDepartment(12L)).thenReturn(1L);
        when(assignmentMapper.countGrantAuthority(
                "operator-01", 10, "DEPARTMENT", 12L
        )).thenReturn(1L);
        when(assignmentMapper.selectRoleId("DEPARTMENT_ASSISTANT")).thenReturn(4L);
        when(assignmentMapper.countAssignment(
                "20240001", 4L, "DEPARTMENT", 12L
        )).thenReturn(0L);
        when(assignmentMapper.insertAssignment(any())).thenAnswer(invocation -> {
            UserRoleScope assignment = invocation.getArgument(0);
            assignment.setId(9L);
            return 1;
        });

        ServiceResult<RoleAssignmentVO> result = service.assign(
                "operator-01",
                request("DEPARTMENT_ASSISTANT", OrgType.DEPARTMENT, 12L)
        );

        assertTrue(result.isSuccess());
        assertEquals("DEPARTMENT_ASSISTANT", result.data().roleCode());
    }

    @Test
    void revokeDeletesAssignment() {
        RoleAssignmentRequest request = request(
                "DEPARTMENT_ADMIN", OrgType.DEPARTMENT, 12L
        );
        when(assignmentMapper.selectRoleId("DEPARTMENT_ADMIN")).thenReturn(3L);
        when(assignmentMapper.countAssignment(
                "20240001", 3L, "DEPARTMENT", 12L
        )).thenReturn(1L);
        when(assignmentMapper.countGrantAuthority(
                "operator-01", 20, "DEPARTMENT", 12L
        )).thenReturn(1L);

        ServiceResult<Void> result = service.revoke("operator-01", request);

        assertTrue(result.isSuccess());
        verify(assignmentMapper).deleteAssignment(
                "20240001", 3L, "DEPARTMENT", 12L
        );
    }

    @Test
    void revokeRejectsMissingAssignment() {
        RoleAssignmentRequest request = request(
                "DEPARTMENT_ADMIN", OrgType.DEPARTMENT, 12L
        );
        when(assignmentMapper.selectRoleId("DEPARTMENT_ADMIN")).thenReturn(3L);
        when(assignmentMapper.countAssignment(
                "20240001", 3L, "DEPARTMENT", 12L
        )).thenReturn(0L);

        ServiceResult<Void> result = service.revoke("operator-01", request);

        assertEquals(BizCode.ROLE_ASSIGNMENT_NOT_FOUND, result.error());
        verify(assignmentMapper, never()).deleteAssignment(
                any(), any(), any(), any()
        );
    }

    @Test
    void revokeRejectsWhenOperatorLacksAuthority() {
        RoleAssignmentRequest request = request(
                "DEPARTMENT_ADMIN", OrgType.DEPARTMENT, 12L
        );
        when(assignmentMapper.selectRoleId("DEPARTMENT_ADMIN")).thenReturn(3L);
        when(assignmentMapper.countAssignment(
                "20240001", 3L, "DEPARTMENT", 12L
        )).thenReturn(1L);
        when(assignmentMapper.countGrantAuthority(
                "operator-01", 20, "DEPARTMENT", 12L
        )).thenReturn(0L);

        ServiceResult<Void> result = service.revoke("operator-01", request);

        assertEquals(BizCode.ROLE_ASSIGNMENT_FORBIDDEN, result.error());
        verify(assignmentMapper, never()).deleteAssignment(
                any(), any(), any(), any()
        );
    }

    @Test
    void revokeRejectsNonAdministratorBeforeCheckingAssignment() {
        when(assignmentMapper.countAdminRole("operator-01")).thenReturn(0L);

        ServiceResult<Void> result = service.revoke(
                "operator-01",
                request("DEPARTMENT_ADMIN", OrgType.DEPARTMENT, 12L)
        );

        assertEquals(BizCode.ROLE_ASSIGNMENT_FORBIDDEN, result.error());
        verify(assignmentMapper, never()).selectRoleId(anyString());
        verify(assignmentMapper, never()).deleteAssignment(
                any(), any(), any(), any()
        );
    }

    @Test
    void revokeRejectsMismatchedScope() {
        RoleAssignmentRequest request = request(
                "BOARD_ADMIN", OrgType.DEPARTMENT, 12L
        );

        ServiceResult<Void> result = service.revoke("operator-01", request);

        assertEquals(BizCode.ROLE_SCOPE_MISMATCH, result.error());
        verify(assignmentMapper, never()).deleteAssignment(
                any(), any(), any(), any()
        );
    }

    private static RoleAssignmentRequest request(
            String roleCode,
            OrgType scopeType,
            Long scopeId
    ) {
        return new RoleAssignmentRequest("20240001", roleCode, scopeType, scopeId);
    }
}
