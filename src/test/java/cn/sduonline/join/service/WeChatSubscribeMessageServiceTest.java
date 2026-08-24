package cn.sduonline.join.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.client.WeChatApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeChatSubscribeMessageServiceTest {

    @Test
    void sendsThroughOfficialAccountSubscribeEndpoint() {
        WeChatApiClient apiClient = mock(WeChatApiClient.class);
        WeChatAccessTokenService tokenService = mock(WeChatAccessTokenService.class);
        when(tokenService.getAccessToken()).thenReturn("access-token");
        when(apiClient.sendSubscribeMessage(
                org.mockito.ArgumentMatchers.eq("access-token"), any()))
                .thenReturn(new WeChatApiClient.TemplateSendResponse(
                        0, "ok", 12345L));
        WeChatSubscribeMessageService service =
                new WeChatSubscribeMessageService(apiClient, tokenService);

        long messageId = service.send(
                "openid-1",
                "template-1",
                Map.of("thing1", new WeChatApiClient.TemplateData("叫号")));

        assertThat(messageId).isEqualTo(12345L);
        verify(apiClient).sendSubscribeMessage(
                org.mockito.ArgumentMatchers.eq("access-token"), any());
    }
}
