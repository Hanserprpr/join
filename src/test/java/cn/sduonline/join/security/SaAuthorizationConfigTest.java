package cn.sduonline.join.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.po.User;
import cn.sduonline.join.service.AuthorizationService;
import cn.sduonline.join.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaAuthorizationConfigTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthorizationService authorizationService;

    private SaAuthorizationConfig config;

    @BeforeEach
    void setUp() {
        config = new SaAuthorizationConfig(userService, authorizationService);
    }

    @Test
    void incompleteAdministratorStillHasRoleAndPermissions() {
        User user = new User();
        user.setCasId("20240001");
        user.setProfileCompleted(false);
        when(userService.findByCasId("20240001")).thenReturn(Optional.of(user));
        when(authorizationService.findRoles("20240001"))
                .thenReturn(List.of("SYSTEM_ADMIN"));

        assertTrue(config.getRoleList("20240001", "login").contains("SYSTEM_ADMIN"));
        assertEquals(List.of("*"), config.getPermissionList("20240001", "login"));
    }
}
