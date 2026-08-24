package cn.sduonline.join.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class WeChatApiClientTest {

    @Test
    void parsesOAuthJsonEvenWhenWeChatReturnsTextPlain() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeChatApiClient client = new WeChatApiClient(
                builder, JsonMapper.builder().build());

        server.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "/sns/oauth2/access_token")))
                .andExpect(queryParam("appid", "wx-app-id"))
                .andExpect(queryParam("code", "oauth-code"))
                .andRespond(withSuccess("""
                        {
                          "access_token": "oauth-token",
                          "expires_in": 7200,
                          "refresh_token": "refresh-token",
                          "openid": "openid-1",
                          "scope": "snsapi_base"
                        }
                        """, MediaType.TEXT_PLAIN));

        WeChatApiClient.OAuthTokenResponse response =
                client.exchangeOAuthCode("wx-app-id", "secret", "oauth-code");

        assertThat(response.openid()).isEqualTo("openid-1");
        assertThat(response.scope()).isEqualTo("snsapi_base");
        server.verify();
    }

    @Test
    void stableTokenPostIncludesJsonContentLength() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeChatApiClient client = new WeChatApiClient(
                builder, JsonMapper.builder().build());

        server.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "/cgi-bin/stable_token")))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(header(
                        "Content-Length",
                        org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
                .andRespond(withSuccess("""
                        {"access_token":"global-token","expires_in":7200}
                        """, MediaType.TEXT_PLAIN));

        WeChatApiClient.StableTokenResponse response =
                client.getStableToken("wx-app-id", "secret", false);

        assertThat(response.accessToken()).isEqualTo("global-token");
        server.verify();
    }

    @Test
    void getsJsApiTicketWithGlobalAccessToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeChatApiClient client = new WeChatApiClient(
                builder, JsonMapper.builder().build());

        server.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "/cgi-bin/ticket/getticket")))
                .andExpect(queryParam("access_token", "global-token"))
                .andExpect(queryParam("type", "jsapi"))
                .andRespond(withSuccess("""
                        {"errcode":0,"errmsg":"ok","ticket":"ticket-1","expires_in":7200}
                        """, MediaType.TEXT_PLAIN));

        WeChatApiClient.JsApiTicketResponse response =
                client.getJsApiTicket("global-token");

        assertThat(response.ticket()).isEqualTo("ticket-1");
        assertThat(response.expiresIn()).isEqualTo(7200);
        server.verify();
    }

    @Test
    void sendsOfficialAccountSubscribeMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeChatApiClient client = new WeChatApiClient(
                builder, JsonMapper.builder().build());

        server.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "/cgi-bin/message/subscribe/bizsend")))
                .andExpect(queryParam("access_token", "global-token"))
                .andExpect(jsonPath("$.touser").value("openid-1"))
                .andExpect(jsonPath("$.template_id").value("template-1"))
                .andExpect(jsonPath("$.data.thing1.value").value("叫号通知"))
                .andRespond(withSuccess("""
                        {"errcode":0,"errmsg":"ok","msgid":12345}
                        """, MediaType.TEXT_PLAIN));

        var response = client.sendSubscribeMessage(
                "global-token",
                new WeChatApiClient.SubscribeMessageRequest(
                        "openid-1",
                        "template-1",
                        null,
                        null,
                        java.util.Map.of(
                                "thing1",
                                new WeChatApiClient.TemplateData("叫号通知"))));

        assertThat(response.messageId()).isEqualTo(12345);
        server.verify();
    }
}
