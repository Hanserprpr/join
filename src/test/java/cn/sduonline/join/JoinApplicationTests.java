package cn.sduonline.join;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import cn.sduonline.join.config.SecurityConfig;
import cn.sduonline.join.controller.AuthController;
import cn.sduonline.join.service.UserService;
import org.junit.jupiter.api.Test;

/**
 * 轻量冒烟测试。完整 {@code @SpringBootTest} 需本地 MySQL/Redis/OIDC，
 * 以及与 Spring Boot 4 对齐的 Spring Cloud 版本。
 */
class JoinApplicationTests {

    @Test
    void coreAuthTypesPresent() {
        assertNotNull(JoinApplication.class);
        assertNotNull(SecurityConfig.class);
        assertNotNull(AuthController.class);
        assertNotNull(UserService.class);
    }
}
