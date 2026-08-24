package cn.sduonline.join.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.config.WeChatProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class WeChatJsApiTicketServiceTest {

    @Test
    void cachesUsableTicket() {
        WeChatApiClient apiClient = mock(WeChatApiClient.class);
        WeChatAccessTokenService tokenService = mock(WeChatAccessTokenService.class);
        WeChatProperties properties = new WeChatProperties();
        properties.setTicketRefreshAheadSeconds(300);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);
        when(tokenService.getAccessToken()).thenReturn("access-token");
        when(apiClient.getJsApiTicket("access-token")).thenReturn(
                new WeChatApiClient.JsApiTicketResponse(
                        "ticket-1", 7200L, 0, "ok"));
        WeChatJsApiTicketService service = new WeChatJsApiTicketService(
                apiClient, tokenService, properties, clock);

        assertThat(service.getTicket()).isEqualTo("ticket-1");
        assertThat(service.getTicket()).isEqualTo("ticket-1");

        verify(apiClient, times(1)).getJsApiTicket("access-token");
    }

    @Test
    void refreshesAccessTokenWhenWeChatRejectsIt() {
        WeChatApiClient apiClient = mock(WeChatApiClient.class);
        WeChatAccessTokenService tokenService = mock(WeChatAccessTokenService.class);
        WeChatProperties properties = new WeChatProperties();
        when(tokenService.getAccessToken()).thenReturn("expired-token");
        when(tokenService.forceRefresh()).thenReturn("new-token");
        when(apiClient.getJsApiTicket("expired-token")).thenReturn(
                new WeChatApiClient.JsApiTicketResponse(
                        null, null, 42001, "access_token expired"));
        when(apiClient.getJsApiTicket("new-token")).thenReturn(
                new WeChatApiClient.JsApiTicketResponse(
                        "ticket-2", 7200L, 0, "ok"));
        WeChatJsApiTicketService service = new WeChatJsApiTicketService(
                apiClient,
                tokenService,
                properties,
                Clock.systemUTC());

        assertThat(service.getTicket()).isEqualTo("ticket-2");
        verify(tokenService).forceRefresh();
    }
}
