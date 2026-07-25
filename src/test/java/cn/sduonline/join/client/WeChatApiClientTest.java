package cn.sduonline.join.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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
}
