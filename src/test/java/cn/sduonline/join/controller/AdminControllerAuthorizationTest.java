package cn.sduonline.join.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.sduonline.join.data.dto.OrganizationNameUpdateRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AdminControllerAuthorizationTest {

    @Test
    void organizationRenameEndpointsRequireSystemAdmin() throws Exception {
        assertSystemAdmin("updateBoardName");
        assertSystemAdmin("updateWorkstationName");
        assertSystemAdmin("updateDepartmentName");
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
