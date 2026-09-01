package cn.sduonline.join.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class WeChatBindingServiceTest {

    private WeChatApiClient apiClient;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private UserMapper userMapper;
    private WeChatTemplateMessageService templateMessageService;
    private WeChatBindingService service;
    private WeChatProperties properties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        apiClient = mock(WeChatApiClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        userMapper = mock(UserMapper.class);
        templateMessageService = mock(WeChatTemplateMessageService.class);
        properties = new WeChatProperties();
        properties.setAppId("wx-app-id");
        properties.setAppSecret("app-secret");
        properties.setOauthCallbackUrl(
                "https://api.example.com/api/wechat/binding/callback");
        properties.setBindingResultUrl(
                "https://web.example.com/wechat-binding");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new WeChatBindingService(
                apiClient, properties, redisTemplate, userMapper,
                templateMessageService);
    }

    @Test
    void createsOneTimeAuthorizationUrlForCurrentUser() {
        User user = user("20240001", null);
        when(userMapper.selectById("20240001")).thenReturn(user);

        String url = service.createAuthorizationUrl("20240001");

        assertThat(url)
                .startsWith("https://open.weixin.qq.com/connect/oauth2/authorize")
                .contains("appid=wx-app-id")
                .contains("scope=snsapi_base")
                .contains("state=")
                .endsWith("#wechat_redirect");
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.startsWith(
                        "join:wechat:binding:state:"),
                eq("20240001"),
                eq(Duration.ofSeconds(600)));
    }

    @Test
    void consumesStateAndBindsReturnedOpenId() {
        User user = user("20240001", null);
        when(valueOperations.getAndDelete("join:wechat:binding:state:state-1"))
                .thenReturn("20240001");
        when(apiClient.exchangeOAuthCode("wx-app-id", "app-secret", "code-1"))
                .thenReturn(new WeChatApiClient.OAuthTokenResponse(
                        "oauth-token", 7200L, "refresh-token",
                        "openid-1", "snsapi_base", null, null));
        when(userMapper.selectById("20240001")).thenReturn(user);
        when(userMapper.selectOne(any())).thenReturn(null);

        service.completeBinding("code-1", "state-1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getWechatOpenid()).isEqualTo("openid-1");
        verify(valueOperations).getAndDelete(
                "join:wechat:binding:state:state-1");
        verify(templateMessageService).send(
                eq("openid-1"),
                eq(""),
                any());
    }

    @Test
    void rejectsExpiredOrAlreadyConsumedState() {
        when(valueOperations.getAndDelete("join:wechat:binding:state:expired"))
                .thenReturn(null);

        assertThatThrownBy(() -> service.completeBinding("code-1", "expired"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效或已过期");
    }

    @Test
    void unbindExplicitlyClearsWechatOpenId() {
        User user = user("20240001", "openid-1");
        when(userMapper.selectById("20240001")).thenReturn(user);

        service.unbind("20240001");

        verify(userMapper).clearWechatOpenid("20240001");
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void duplicateWechatEventForSameBindingIsIdempotent() {
        User user = user("20240001", "openid-1");
        when(userMapper.selectById("20240001")).thenReturn(user);

        service.bindOpenId("20240001", "openid-1");

        verify(userMapper, never()).updateById(any(User.class));
        verify(templateMessageService, never()).send(
                any(), any(), any());
    }

    private User user(String casId, String openId) {
        User user = new User();
        user.setCasId(casId);
        user.setName("测试用户");
        user.setWechatOpenid(openId);
        return user;
    }
}
