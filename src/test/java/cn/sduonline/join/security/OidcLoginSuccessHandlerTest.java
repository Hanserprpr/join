package cn.sduonline.join.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.CheckInQrGrant;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.service.DepartmentCheckInService;
import cn.sduonline.join.service.ServiceResult;
import cn.sduonline.join.service.UserService;
import cn.sduonline.join.service.WeChatBindingService;
import cn.sduonline.join.service.WeChatLoginService;
import cn.sduonline.join.service.WeChatPendingLinkStore;
import cn.sduonline.join.service.WeChatPendingLinkStore.PendingLink;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class OidcLoginSuccessHandlerTest {

    private UserService userService;
    private WeChatPendingLinkStore pendingLinkStore;
    private WeChatBindingService bindingService;
    private WeChatLoginService weChatLoginService;
    private DepartmentCheckInService checkInService;
    private OidcLoginSuccessHandler handler;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        pendingLinkStore = new WeChatPendingLinkStore();
        bindingService = mock(WeChatBindingService.class);
        weChatLoginService = mock(WeChatLoginService.class);
        checkInService = mock(DepartmentCheckInService.class);

        AppProperties appProperties = new AppProperties();
        appProperties.setFrontendUrl("https://web.example.com");
        handler = new OidcLoginSuccessHandler(
                userService, appProperties, pendingLinkStore,
                bindingService, weChatLoginService, checkInService);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("20240001");
        authentication = new UsernamePasswordAuthenticationToken(
                oidcUser, "n/a");
        when(userService.syncFromOidc(any())).thenReturn(user());
    }

    @Test
    void withoutPendingLinkKeepsPlainFrontendRedirect() throws IOException {
        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            handler.onAuthenticationSuccess(request, response, authentication);
        }

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://web.example.com/?login=success");
        verify(bindingService, never()).bindOpenId(any(), any());
    }

    @Test
    void pendingLinkBindsOpenIdAndFinishesCheckIn() throws IOException {
        CheckInQrGrant grant = new CheckInQrGrant(12L, 30L);
        pendingLinkStore.save(request, new PendingLink("openid-1", grant));
        ServiceResult<CheckInVO> checkInResult = ServiceResult.success(
                new CheckInVO(1L, 12L, 30L, 40L, null, 7, false));
        when(checkInService.checkInCaptured(grant, "20240001"))
                .thenReturn(checkInResult);
        when(weChatLoginService.checkInResultUrl(checkInResult))
                .thenReturn("https://web.example.com/check-in?status=success");

        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            handler.onAuthenticationSuccess(request, response, authentication);
        }

        verify(bindingService).bindOpenId("20240001", "openid-1");
        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://web.example.com/check-in?status=success");
        assertThat(pendingLinkStore.take(request)).isNull();
    }

    @Test
    void pendingLinkWithoutCheckInReturnsToFrontendWithBindingResult()
            throws IOException {
        pendingLinkStore.save(request, new PendingLink("openid-1", null));

        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            handler.onAuthenticationSuccess(request, response, authentication);
        }

        verify(bindingService).bindOpenId("20240001", "openid-1");
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "https://web.example.com/?login=success"
                        + "&source=wechat&binding=success");
    }

    @Test
    void bindingFailureStillCompletesLogin() throws IOException {
        pendingLinkStore.save(request, new PendingLink("openid-1", null));
        doThrow(new IllegalStateException("该微信已经绑定其他用户"))
                .when(bindingService).bindOpenId("20240001", "openid-1");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            handler.onAuthenticationSuccess(request, response, authentication);
            stpUtil.verify(() -> StpUtil.login("20240001"));
        }

        assertThat(response.getRedirectedUrl()).isEqualTo(
                "https://web.example.com/?login=success"
                        + "&source=wechat&binding=failed");
    }

    private User user() {
        User user = new User();
        user.setCasId("20240001");
        user.setName("测试用户");
        return user;
    }
}
