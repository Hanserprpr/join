package cn.sduonline.join.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.CheckInQrGrant;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.service.DepartmentCheckInService;
import cn.sduonline.join.service.ServiceResult;
import cn.sduonline.join.service.WeChatLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class WeChatCheckInControllerTest {

    private DepartmentCheckInService checkInService;
    private WeChatLoginService loginService;
    private WeChatCheckInController controller;

    @BeforeEach
    void setUp() {
        checkInService = mock(DepartmentCheckInService.class);
        loginService = mock(WeChatLoginService.class);
        controller = new WeChatCheckInController(checkInService, loginService);
    }

    @Test
    void alreadyLoggedInUserChecksInWithoutWechatAuthorization() {
        CheckInQrGrant grant = new CheckInQrGrant(12L, 30L);
        ServiceResult<CheckInVO> checkInResult = ServiceResult.success(
                new CheckInVO(1L, 12L, 30L, 40L, null, 7, false));
        when(checkInService.captureQrGrant("token-1"))
                .thenReturn(ServiceResult.success(grant));
        when(checkInService.checkInCaptured(grant, "20240001"))
                .thenReturn(checkInResult);
        when(loginService.checkInResultUrl(checkInResult))
                .thenReturn("https://web.example.com/check-in?status=success");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(true);
            stpUtil.when(StpUtil::getLoginIdAsString)
                    .thenReturn("20240001");

            var redirect = controller.entry("token-1");

            assertThat(redirect.getUrl())
                    .isEqualTo(
                            "https://web.example.com/check-in?status=success");
        }
        verify(loginService, never()).createCheckInAuthorizationUrl(grant);
    }

    @Test
    void anonymousUserStartsWechatAuthorizationWithCapturedGrant() {
        CheckInQrGrant grant = new CheckInQrGrant(12L, 30L);
        when(checkInService.captureQrGrant("token-1"))
                .thenReturn(ServiceResult.success(grant));
        when(loginService.createCheckInAuthorizationUrl(grant))
                .thenReturn("https://open.weixin.qq.com/oauth");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(false);

            var redirect = controller.entry("token-1");

            assertThat(redirect.getUrl())
                    .isEqualTo("https://open.weixin.qq.com/oauth");
        }
        verify(checkInService, never()).checkInCaptured(grant, "20240001");
    }
}
