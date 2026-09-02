package cn.sduonline.join.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.service.WeChatBindSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class WeChatEventControllerTest {

    private WeChatBindSessionService sessionService;
    private WeChatEventController controller;

    private static final String TOKEN = "test-token";
    // sha1 of the sorted concatenation of token=test-token, timestamp=1700000000, nonce=123456
    private static final String VALID_SIGNATURE =
            "51eceab7903acb17f5057dfac60fa898414d9553";

    @BeforeEach
    void setUp() {
        WeChatProperties properties = new WeChatProperties();
        properties.setServerToken(TOKEN);
        sessionService = mock(WeChatBindSessionService.class);
        controller = new WeChatEventController(properties, sessionService);
    }

    @Test
    void echosBackChallengeOnValidSignature() {
        ResponseEntity<String> response = controller.verify(
                VALID_SIGNATURE, "1700000000", "123456", "the-echo-string");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("the-echo-string");
    }

    @Test
    void rejectsHandshakeWithBadSignature() {
        ResponseEntity<String> response = controller.verify(
                "wrong-signature", "1700000000", "123456", "the-echo-string");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void completesPendingBindOnSubscribeEvent() {
        String xml = """
                <xml>
                  <ToUserName><![CDATA[gh_123]]></ToUserName>
                  <FromUserName><![CDATA[openid-1]]></FromUserName>
                  <CreateTime>1700000000</CreateTime>
                  <MsgType><![CDATA[event]]></MsgType>
                  <Event><![CDATA[subscribe]]></Event>
                </xml>
                """;

        ResponseEntity<String> response = controller.receive(
                VALID_SIGNATURE, "1700000000", "123456", xml);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(sessionService).completeFollowUp("openid-1");
    }

    @Test
    void ignoresNonSubscribeEventsAndBadSignature() {
        String xml = """
                <xml>
                  <FromUserName><![CDATA[openid-1]]></FromUserName>
                  <MsgType><![CDATA[text]]></MsgType>
                </xml>
                """;

        controller.receive(VALID_SIGNATURE, "1700000000", "123456", xml);
        ResponseEntity<String> rejected = controller.receive(
                "wrong-signature", "1700000000", "123456", xml);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(sessionService, never()).completeFollowUp(org.mockito.ArgumentMatchers.any());
    }
}
