package cn.sduonline.join.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.sduonline.join.config.WeChatProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WeChatJsSdkServiceTest {

    private WeChatJsApiTicketService ticketService;
    private WeChatJsSdkService service;

    @BeforeEach
    void setUp() {
        ticketService = mock(WeChatJsApiTicketService.class);
        when(ticketService.getTicket()).thenReturn("ticket-1");
        WeChatProperties properties = new WeChatProperties();
        properties.setAppId("wx-app-id");
        properties.setAppSecret("app-secret");
        properties.setJsSdkAllowedOrigins(List.of("https://join.example.com"));
        properties.setSubscribeTemplateIds(List.of(
                " template-1 ", "template-2", "template-1", ""));
        service = new WeChatJsSdkService(
                ticketService,
                properties,
                Clock.fixed(
                        Instant.ofEpochSecond(1_777_777_777L),
                        ZoneOffset.UTC));
    }

    @Test
    void signsExactUrlWithoutFragmentAndReturnsTemplates() throws Exception {
        var config = service.createConfig(
                "https://join.example.com/interview?room=1#queue");

        String source = "jsapi_ticket=ticket-1"
                + "&noncestr=" + config.nonceStr()
                + "&timestamp=1777777777"
                + "&url=https://join.example.com/interview?room=1";
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(
                        source.getBytes(StandardCharsets.UTF_8)));
        assertThat(config.appId()).isEqualTo("wx-app-id");
        assertThat(config.timestamp()).isEqualTo(1_777_777_777L);
        assertThat(config.signature()).isEqualTo(expected);
        assertThat(config.templateIds()).containsExactly(
                "template-1", "template-2");
    }

    @Test
    void rejectsUrlFromUnconfiguredOrigin() {
        assertThatThrownBy(() -> service.createConfig(
                "https://evil.example.com/interview"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("域名不允许");
    }
}
