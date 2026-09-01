package cn.sduonline.join.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.WeChatBindSessionCreatedVO;
import cn.sduonline.join.service.WeChatBindSessionService;
import cn.sduonline.join.service.WeChatBindingService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

class WeChatBindingControllerTest {

    @Test
    void legacyUrlEndpointReturnsNewBindingLinkUnderOldFieldName() {
        WeChatBindingService bindingService = mock(WeChatBindingService.class);
        WeChatBindSessionService sessionService =
                mock(WeChatBindSessionService.class);
        WeChatBindingController controller = new WeChatBindingController(
                bindingService, sessionService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        String bindingUrl =
                "https://api.example.com/api/wechat/bind/start?token=wb_abc";
        when(sessionService.create(
                "20240001", request.getSession().getId()))
                .thenReturn(new WeChatBindSessionCreatedVO(
                        "wb_abc", "WAITING", bindingUrl,
                        Instant.parse("2026-09-01T02:05:00Z"), 300));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("20240001");

            var result = controller.authorizationUrl(request);

            assertThat(result.getData().get("authorizationUrl"))
                    .isEqualTo(bindingUrl);
            assertThat(result.getData().get("bindingUrl"))
                    .isEqualTo(bindingUrl);
            assertThat(result.getData().get("sessionId"))
                    .isEqualTo("wb_abc");
            assertThat(result.getData().get("expiresIn"))
                    .isEqualTo("300");
        }
    }
}
