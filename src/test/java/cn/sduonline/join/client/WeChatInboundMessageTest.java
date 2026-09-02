package cn.sduonline.join.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WeChatInboundMessageTest {

    @Test
    void parsesSubscribeEvent() {
        String xml = """
                <xml>
                  <ToUserName><![CDATA[gh_123]]></ToUserName>
                  <FromUserName><![CDATA[openid-1]]></FromUserName>
                  <CreateTime>1700000000</CreateTime>
                  <MsgType><![CDATA[event]]></MsgType>
                  <Event><![CDATA[subscribe]]></Event>
                </xml>
                """;

        WeChatInboundMessage message = WeChatInboundMessage.parse(xml);

        assertThat(message.fromUserName()).isEqualTo("openid-1");
        assertThat(message.isSubscribeEvent()).isTrue();
    }

    @Test
    void doesNotTreatOrdinaryTextMessageAsSubscribeEvent() {
        String xml = """
                <xml>
                  <ToUserName><![CDATA[gh_123]]></ToUserName>
                  <FromUserName><![CDATA[openid-1]]></FromUserName>
                  <CreateTime>1700000000</CreateTime>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[hello]]></Content>
                </xml>
                """;

        WeChatInboundMessage message = WeChatInboundMessage.parse(xml);

        assertThat(message.isSubscribeEvent()).isFalse();
    }

    @Test
    void rejectsBlankBody() {
        assertThatThrownBy(() -> WeChatInboundMessage.parse(" "))
                .isInstanceOf(WeChatApiException.class);
    }
}
