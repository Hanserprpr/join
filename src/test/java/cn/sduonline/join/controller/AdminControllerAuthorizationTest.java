package cn.sduonline.join.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.sduonline.join.data.dto.OrganizationNameUpdateRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AdminControllerAuthorizationTest {

    @Test
    void organizationRenameEndpointsRequireSystemAdmin() throws Exception {
        assertSystemAdmin("updateBoardName");
        assertSystemAdmin("updateWorkstationName");
        assertSystemAdmin("updateDepartmentName");
    }

    /**
     * 分页用户列表可枚举全量用户，必须限定平台管理员；
     * 学号联想接口 searchUsers 面向下级管理员，两者不能混用同一权限。
     */
    @Test
    void pagedUserListRequiresSystemAdmin() {
        SaCheckRole annotation = method("findUsers")
                .getAnnotation(SaCheckRole.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[]{"SYSTEM_ADMIN"}, annotation.value());
    }

    @Test
    void roleAssignmentUserLookupStaysOpenToLowerAdmins() {
        assertNull(method("searchUsers").getAnnotation(SaCheckRole.class));
    }

    private static Method method(String name) {
        return Arrays.stream(AdminController.class.getMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void assertSystemAdmin(String methodName) throws Exception {
        Method method = AdminController.class.getMethod(
                methodName, Long.class, OrganizationNameUpdateRequest.class
        );
        SaCheckRole annotation = method.getAnnotation(SaCheckRole.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{"SYSTEM_ADMIN"}, annotation.value());
    }
}
