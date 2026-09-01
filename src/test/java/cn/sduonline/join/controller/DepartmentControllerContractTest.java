package cn.sduonline.join.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cn.sduonline.join.security.scope.DepartmentPermission;
import cn.sduonline.join.security.scope.PermissionCode;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

class DepartmentControllerContractTest {

    @Test
    void achievementImageUploadUsesDedicatedMultipartEndpoint()
            throws NoSuchMethodException {
        Method method = DepartmentController.class.getMethod(
                "uploadAchievementImage", Long.class, MultipartFile.class
        );

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        DepartmentPermission permission = method.getAnnotation(
                DepartmentPermission.class
        );

        assertNotNull(mapping);
        assertArrayEquals(
                new String[]{"/{departmentId}/achievements/images/upload"},
                mapping.value()
        );
        assertArrayEquals(new String[]{"multipart/form-data"}, mapping.consumes());
        assertNotNull(permission);
        assertEquals(PermissionCode.RECRUITMENT_MANAGE, permission.value());
    }
}
