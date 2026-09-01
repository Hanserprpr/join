package cn.sduonline.join.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.CheckInQrGrant;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.service.DepartmentCheckInService;
import cn.sduonline.join.service.WeChatLoginService;
import cn.sduonline.join.service.WeChatLoginService.AuthenticationResult;
import cn.sduonline.join.service.WeChatPendingLinkStore;
import cn.sduonline.join.service.WeChatPendingLinkStore.PendingLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

class WeChatLoginControllerTest {

    private WeChatLoginService loginService;
    private DepartmentCheckInService checkInService;
    private WeChatPendingLinkStore pendingLinkStore;
    private WeChatLoginController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        loginService = mock(WeChatLoginService.class);
        checkInService = mock(DepartmentCheckInService.class);
        pendingLinkStore = new WeChatPendingLinkStore();
        controller = new WeChatLoginController(
                loginService, checkInService, pendingLinkStore);
        request = new MockHttpServletRequest();
        request.setContextPath("/recruit");
    }

    @Test
    void unboundOpenIdGoesToUnifiedAuthWithPendingLinkStashed() {
        CheckInQrGrant grant = new CheckInQrGrant(12L, 30L);
        when(loginService.authenticate("code-1", "state-1"))
                .thenReturn(new AuthenticationResult(null, "openid-1", grant));
        when(loginService.unifiedAuthUrl("/recruit"))
                .thenReturn("/recruit/api/oauth2/authorization/sdu");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            var redirect = controller.callback("code-1", "state-1", request);

            assertThat(redirect.getUrl())
                    .isEqualTo("/recruit/api/oauth2/authorization/sdu");
            stpUtil.verifyNoInteractions();
        }
        assertThat(pendingLinkStore.take(request))
                .isEqualTo(new PendingLink("openid-1", grant));
        verify(loginService, never()).loginResultUrl(false);
    }

    @Test
    void boundOpenIdLogsInWithoutUnifiedAuth() {
        User user = new User();
        user.setCasId("20240001");
        when(loginService.authenticate("code-1", "state-1"))
                .thenReturn(new AuthenticationResult(user, "openid-1", null));
        when(loginService.loginResultUrl(true))
                .thenReturn("https://web.example.com/?login=success");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            var redirect = controller.callback("code-1", "state-1", request);

            assertThat(redirect.getUrl())
                    .isEqualTo("https://web.example.com/?login=success");
            stpUtil.verify(() -> StpUtil.login("20240001"));
        }
        assertThat(pendingLinkStore.take(request)).isNull();
    }

    @Test
    void failedAuthenticationStillFallsBackToFrontendResultPage() {
        when(loginService.authenticate("code-1", "state-1"))
                .thenThrow(new IllegalArgumentException("微信登录链接无效或已过期"));
        when(loginService.loginResultUrl(false))
                .thenReturn("https://web.example.com/?login=failed");

        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            var redirect = controller.callback("code-1", "state-1", request);

            assertThat(redirect.getUrl())
                    .isEqualTo("https://web.example.com/?login=failed");
        }
    }
}
