package cn.sduonline.join.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class WeChatBindSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T02:00:00Z");

    private WeChatApiClient apiClient;
    private WeChatBindingService bindingService;
    private StringRedisTemplate redisTemplate;
    private HashOperations<String, String, String> hashOperations;
    private ValueOperations<String, String> valueOperations;
    private UserMapper userMapper;
    private WeChatBindSessionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        apiClient = mock(WeChatApiClient.class);
        bindingService = mock(WeChatBindingService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        valueOperations = mock(ValueOperations.class);
        userMapper = mock(UserMapper.class);

        when(redisTemplate.<String, String>opsForHash())
                .thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        WeChatProperties properties = properties();
        service = new WeChatBindSessionService(
                apiClient, bindingService, properties,
                redisTemplate, userMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsFiveMinuteBindingLinkForFrontendQrCode() {
        User user = user("20240001", null);
        when(userMapper.selectById("20240001")).thenReturn(user);

        var result = service.create("20240001", "browser-session-1");

        assertThat(result.sessionId()).startsWith("wb_");
        assertThat(result.status()).isEqualTo("WAITING");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(result.expiresIn()).isEqualTo(300);
        assertThat(result.bindingUrl())
                .contains("/api/wechat/bind/start?token=wb_");
        verify(redisTemplate).expire(
                "join:wechat:bind:session:" + result.sessionId(),
                Duration.ofSeconds(300));
    }

    @Test
    void pollingRequiresOriginalUserAndBrowserSession() {
        String token = "wb_scene-token";
        when(hashOperations.entries(
                "join:wechat:bind:session:" + token))
                .thenReturn(Map.of(
                        "casId", "20240001",
                        "browserSessionId", "browser-session-1",
                        "status", "WAITING",
                        "expiresAt", String.valueOf(
                                NOW.plusSeconds(228).getEpochSecond())
                ));

        var result = service.status(
                token, "20240001", "browser-session-1");
        assertThat(result.status()).isEqualTo("WAITING");
        assertThat(result.expiresIn()).isEqualTo(228);

        assertThatThrownBy(() -> service.status(
                token, "20240001", "another-browser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前浏览器");
    }

    @Test
    void copiedEntryLinkCompletesThroughOneTimeOauthState() {
        String token = "wb_scene-token";
        String sessionKey = "join:wechat:bind:session:" + token;
        Map<String, String> waiting = Map.of(
                "casId", "20240001",
                "browserSessionId", "browser-session-1",
                "status", "WAITING",
                "expiresAt", String.valueOf(
                        NOW.plusSeconds(300).getEpochSecond())
        );
        Map<String, String> bound = Map.of(
                "casId", "20240001",
                "browserSessionId", "browser-session-1",
                "status", "BOUND",
                "expiresAt", String.valueOf(
                        NOW.plusSeconds(300).getEpochSecond())
        );
        when(valueOperations.getAndDelete(
                "join:wechat:bind:oauth-state:state-1"))
                .thenReturn(token);
        when(apiClient.exchangeOAuthCode(
                "wx-app-id", "app-secret", "code-1"))
                .thenReturn(new WeChatApiClient.OAuthTokenResponse(
                        "oauth-token", 7200L, "refresh-token",
                        "openid-1", "snsapi_base", null, null));
        when(hashOperations.entries(sessionKey))
                .thenReturn(waiting, waiting, bound);
        when(valueOperations.setIfAbsent(
                "join:wechat:bind:claim:" + token,
                "openid-1", Duration.ofSeconds(30)))
                .thenReturn(true);

        service.completeOAuth("code-1", "qr_state-1");

        verify(bindingService).bindOpenId("20240001", "openid-1");
        verify(valueOperations).getAndDelete(
                "join:wechat:bind:oauth-state:state-1");
    }

    @Test
    void rejectsCreatingSessionForAlreadyBoundUser() {
        when(userMapper.selectById("20240001"))
                .thenReturn(user("20240001", "openid-existing"));

        assertThatThrownBy(() -> service.create(
                "20240001", "browser-session-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已经绑定");
    }

    private static WeChatProperties properties() {
        WeChatProperties properties = new WeChatProperties();
        properties.setAppId("wx-app-id");
        properties.setAppSecret("app-secret");
        properties.setOauthCallbackUrl(
                "https://api.example.com/api/wechat/binding/callback");
        properties.setBindingResultUrl(
                "https://web.example.com/wechat-binding");
        properties.setBindingEntryUrl(
                "https://api.example.com/api/wechat/bind/start");
        properties.setBindingSessionOauthCallbackUrl(
                "https://api.example.com/api/wechat/bind/oauth/callback");
        properties.setBindingSessionTtlSeconds(300);
        return properties;
    }

    private static User user(String casId, String openid) {
        User user = new User();
        user.setCasId(casId);
        user.setName("测试用户");
        user.setWechatOpenid(openid);
        return user;
    }
}
