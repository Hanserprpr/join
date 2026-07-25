package cn.sduonline.join.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class WeChatApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WeChatApiClient(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl("https://api.weixin.qq.com").build();
        this.objectMapper = objectMapper;
    }

    public StableTokenResponse getStableToken(
            String appId, String appSecret, boolean forceRefresh) {
        StableTokenRequest request = new StableTokenRequest(
                "client_credential", appId, appSecret, forceRefresh);
        String json = writeJson(request);
        String responseBody = restClient.post()
                .uri("/cgi-bin/stable_token")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(json.getBytes(StandardCharsets.UTF_8).length)
                .body(json)
                .retrieve()
                .body(String.class);
        return readJson(responseBody, StableTokenResponse.class, "获取 access_token");
    }

    public TemplateSendResponse sendTemplateMessage(
            String accessToken, TemplateMessageRequest request) {
        String json = writeJson(request);
        String responseBody = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/message/template/send")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(json.getBytes(StandardCharsets.UTF_8).length)
                .body(json)
                .retrieve()
                .body(String.class);
        return readJson(responseBody, TemplateSendResponse.class, "发送模板消息");
    }

    public OAuthTokenResponse exchangeOAuthCode(
            String appId, String appSecret, String code) {
        String responseBody = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sns/oauth2/access_token")
                        .queryParam("appid", appId)
                        .queryParam("secret", appSecret)
                        .queryParam("code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(String.class);
        return readJson(responseBody, OAuthTokenResponse.class, "网页授权");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new WeChatApiException("构造微信接口请求失败");
        }
    }

    private <T> T readJson(String responseBody, Class<T> responseType, String action) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new WeChatApiException("微信" + action + "接口返回空响应");
        }
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JacksonException exception) {
            throw new WeChatApiException("微信" + action + "响应不是有效 JSON");
        }
    }

    public record StableTokenRequest(
            @JsonProperty("grant_type") String grantType,
            String appid,
            String secret,
            @JsonProperty("force_refresh") boolean forceRefresh) {
    }

    public record StableTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            Integer errcode,
            String errmsg) {
    }

    public record TemplateMessageRequest(
            @JsonProperty("touser") String toUser,
            @JsonProperty("template_id") String templateId,
            String url,
            @JsonProperty("miniprogram") MiniProgram miniProgram,
            Map<String, TemplateData> data) {
    }

    public record MiniProgram(String appid, @JsonProperty("pagepath") String pagePath) {
    }

    public record TemplateData(String value, String color) {
        public TemplateData(String value) {
            this(value, null);
        }
    }

    public record TemplateSendResponse(
            Integer errcode,
            String errmsg,
            @JsonProperty("msgid") Long messageId) {
    }

    public record OAuthTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            String openid,
            String scope,
            Integer errcode,
            String errmsg) {
    }
}
