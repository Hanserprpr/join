package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.DepartmentAccessVO;
import cn.sduonline.join.data.dto.DepartmentIdentityVO;
import cn.sduonline.join.data.dto.DepartmentRoleAccess;
import cn.sduonline.join.data.dto.PermissionDepartmentAccess;
import cn.sduonline.join.mapper.AuthorizationMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private AuthorizationMapper authorizationMapper;

    private AuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationService(authorizationMapper);
    }

    @Test
    void findManagedDepartmentsGroupsByPermission() {
        when(authorizationMapper.selectScopedDepartmentAccess("20240001"))
                .thenReturn(List.of(
                        new PermissionDepartmentAccess("recruitment:manage", 1L),
                        new PermissionDepartmentAccess("recruitment:manage", 2L),
                        new PermissionDepartmentAccess("application:read", 1L)
                ));

        Map<String, List<Long>> result = service.findManagedDepartments("20240001");

        assertEquals(List.of(1L, 2L), result.get("recruitment:manage"));
        assertEquals(List.of(1L), result.get("application:read"));
    }

    @Test
    void findManagedDepartmentsMapsSystemAdminToWildcard() {
        when(authorizationMapper.selectScopedDepartmentAccess("20240001"))
                .thenReturn(List.of(
                        new PermissionDepartmentAccess("*", 1L),
                        new PermissionDepartmentAccess("*", 2L)
                ));

        Map<String, List<Long>> result = service.findManagedDepartments("20240001");

        assertEquals(List.of(1L, 2L), result.get("*"));
    }

    @Test
    void findManagedDepartmentsReturnsEmptyMapWhenNoScope() {
        when(authorizationMapper.selectScopedDepartmentAccess("20240001"))
                .thenReturn(List.of());

        Map<String, List<Long>> result = service.findManagedDepartments("20240001");

        assertTrue(result.isEmpty());
    }

    @Test
    void findDepartmentAccessReturnsBothDepartmentsForAssistantIdentity() {
        when(authorizationMapper.selectDepartmentRoleAccess("20240001"))
                .thenReturn(List.of(
                        new DepartmentRoleAccess(
                                5L, "后端部", "DEPARTMENT_ASSISTANT",
                                "辅助管理员", "DEPARTMENT", "application:read"
                        ),
                        new DepartmentRoleAccess(
                                5L, "后端部", "DEPARTMENT_ASSISTANT",
                                "辅助管理员", "DEPARTMENT", "check-in:manage"
                        ),
                        new DepartmentRoleAccess(
                                9L, "前端部", "DEPARTMENT_ASSISTANT",
                                "辅助管理员", "DEPARTMENT", "application:read"
                        )
                ));

        List<DepartmentAccessVO> result = service.findDepartmentAccess("20240001");

        assertEquals(2, result.size());
        DepartmentAccessVO first = result.getFirst();
        assertEquals(5L, first.departmentId());
        assertEquals("后端部", first.departmentName());
        assertEquals(1, first.identities().size());
        DepartmentIdentityVO identity = first.identities().getFirst();
        assertEquals("DEPARTMENT_ASSISTANT", identity.roleCode());
        assertEquals("辅助管理员", identity.roleName());
        assertEquals("DEPARTMENT", identity.scopeType());
        assertEquals(List.of("application:read", "check-in:manage"), identity.permissions());

        DepartmentAccessVO second = result.get(1);
        assertEquals(9L, second.departmentId());
        assertEquals("前端部", second.departmentName());
        assertEquals(
                List.of("application:read"),
                second.identities().getFirst().permissions()
        );
    }

    @Test
    void findDepartmentAccessMapsSystemAdminToWildcard() {
        when(authorizationMapper.selectDepartmentRoleAccess("20240001"))
                .thenReturn(List.of(
                        new DepartmentRoleAccess(
                                5L, "后端部", "SYSTEM_ADMIN",
                                "平台管理员", "ALL", "*"
                        ),
                        new DepartmentRoleAccess(
                                9L, "前端部", "SYSTEM_ADMIN",
                                "平台管理员", "ALL", "*"
                        )
                ));

        List<DepartmentAccessVO> result = service.findDepartmentAccess("20240001");

        assertEquals(2, result.size());
        assertEquals(List.of("*"), result.getFirst().identities().getFirst().permissions());
    }

    @Test
    void findDepartmentAccessDistinguishesMultipleRolesInSameDepartment() {
        when(authorizationMapper.selectDepartmentRoleAccess("20240001"))
                .thenReturn(List.of(
                        new DepartmentRoleAccess(
                                5L, "后端部", "BOARD_ADMIN",
                                "板块管理员", "BOARD", "recruitment:manage"
                        ),
                        new DepartmentRoleAccess(
                                5L, "后端部", "DEPARTMENT_ASSISTANT",
                                "辅助管理员", "DEPARTMENT", "interview:evaluate"
                        )
                ));

        List<DepartmentAccessVO> result = service.findDepartmentAccess("20240001");

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().identities().size());
        assertEquals(
                "BOARD_ADMIN",
                result.getFirst().identities().getFirst().roleCode()
        );
        assertEquals(
                "DEPARTMENT_ASSISTANT",
                result.getFirst().identities().get(1).roleCode()
        );
    }

    @Test
    void findDepartmentAccessReturnsEmptyWhenNoScope() {
        when(authorizationMapper.selectDepartmentRoleAccess("20240001"))
                .thenReturn(List.of());

        List<DepartmentAccessVO> result = service.findDepartmentAccess("20240001");

        assertTrue(result.isEmpty());
    }
}
