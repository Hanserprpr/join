package cn.sduonline.join.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.dto.CheckInQrGrant;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class WeChatLoginServiceTest {

    private WeChatApiClient apiClient;
    private ValueOperations<String, String> valueOperations;
    private UserMapper userMapper;
    private WeChatLoginService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        apiClient = mock(WeChatApiClient.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        userMapper = mock(UserMapper.class);

        WeChatProperties properties = new WeChatProperties();
        properties.setAppId("wx-app-id");
        properties.setAppSecret("app-secret");
        properties.setLoginOauthCallbackUrl(
                "https://api.example.com/api/wechat/login/callback");
        properties.setLoginResultUrl("https://web.example.com/");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AppProperties appProperties = new AppProperties();
        appProperties.getCheckIn().setResultUrl(
                "https://web.example.com/check-in");
        service = new WeChatLoginService(
                apiClient, properties, appProperties,
                redisTemplate, userMapper);
    }

    @Test
    void createsOneTimeLoginAuthorizationUrlWithoutExistingUserSession() {
        String url = service.createAuthorizationUrl();

        assertThat(url)
                .startsWith("https://open.weixin.qq.com/connect/oauth2/authorize")
                .contains("appid=wx-app-id")
                .contains("scope=snsapi_base")
                .contains("state=")
                .contains("api/wechat/login/callback")
                .endsWith("#wechat_redirect");
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.startsWith(
                        "join:wechat:login:state:"),
                eq("login"),
                eq(Duration.ofSeconds(300)));
    }

    @Test
    void authenticatesAlreadyBoundOpenIdAndConsumesState() {
        User boundUser = user("20240001", "openid-1");
        when(valueOperations.getAndDelete("join:wechat:login:state:state-1"))
                .thenReturn("login");
        when(apiClient.exchangeOAuthCode("wx-app-id", "app-secret", "code-1"))
                .thenReturn(new WeChatApiClient.OAuthTokenResponse(
                        "oauth-token", 7200L, "refresh-token",
                        "openid-1", "snsapi_base", null, null));
        when(userMapper.selectOne(any())).thenReturn(boundUser);

        WeChatLoginService.AuthenticationResult result =
                service.authenticate("code-1", "state-1");

        assertThat(result.user().getCasId()).isEqualTo("20240001");
        assertThat(result.openid()).isEqualTo("openid-1");
        assertThat(result.checkInGrant()).isNull();
        verify(valueOperations).getAndDelete(
                "join:wechat:login:state:state-1");
    }

    @Test
    void reportsUnboundOpenIdWithoutUserSoCallerCanFallBackToUnifiedAuth() {
        when(valueOperations.getAndDelete("join:wechat:login:state:state-1"))
                .thenReturn("login");
        when(apiClient.exchangeOAuthCode("wx-app-id", "app-secret", "code-1"))
                .thenReturn(new WeChatApiClient.OAuthTokenResponse(
                        "oauth-token", 7200L, "refresh-token",
                        "openid-1", "snsapi_base", null, null));
        when(userMapper.selectOne(any())).thenReturn(null);

        WeChatLoginService.AuthenticationResult result =
                service.authenticate("code-1", "state-1");

        assertThat(result.user()).isNull();
        assertThat(result.openid()).isEqualTo("openid-1");
    }

    @Test
    void buildsUnifiedAuthUrlUnderReverseProxyContextPath() {
        assertThat(service.unifiedAuthUrl(""))
                .isEqualTo("/api/oauth2/authorization/sdu");
        assertThat(service.unifiedAuthUrl("/recruit"))
                .isEqualTo("/recruit/api/oauth2/authorization/sdu");
    }

    @Test
    void rejectsExpiredOrAlreadyConsumedState() {
        when(valueOperations.getAndDelete("join:wechat:login:state:expired"))
                .thenReturn(null);

        assertThatThrownBy(() -> service.authenticate("code-1", "expired"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效或已过期");
    }

    @Test
    void buildsFrontendResultUrl() {
        assertThat(service.loginResultUrl(true))
                .isEqualTo(
                        "https://web.example.com/?login=success&source=wechat");
        assertThat(service.loginResultUrl(false))
                .isEqualTo(
                        "https://web.example.com/?login=failed&source=wechat");
    }

    @Test
    void preservesCapturedCheckInGrantAcrossWechatAuthorization() {
        String url = service.createCheckInAuthorizationUrl(
                new CheckInQrGrant(12L, 30L));
        assertThat(url).contains("state=");
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.startsWith(
                        "join:wechat:login:state:"),
                eq("check-in:12:30"),
                eq(Duration.ofSeconds(120)));

        when(valueOperations.getAndDelete(
                "join:wechat:login:state:state-check-in"))
                .thenReturn("check-in:12:30");
        when(apiClient.exchangeOAuthCode(
                "wx-app-id", "app-secret", "code-check-in"))
                .thenReturn(new WeChatApiClient.OAuthTokenResponse(
                        "oauth-token", 7200L, "refresh-token",
                        "openid-1", "snsapi_base", null, null));
        when(userMapper.selectOne(any()))
                .thenReturn(user("20240001", "openid-1"));

        WeChatLoginService.AuthenticationResult result =
                service.authenticate("code-check-in", "state-check-in");

        assertThat(result.checkInGrant())
                .isEqualTo(new CheckInQrGrant(12L, 30L));
    }

    @Test
    void buildsCheckInResultUrlsForFrontend() {
        String success = service.checkInResultUrl(ServiceResult.success(
                new CheckInVO(
                        1L, 12L, 30L, 40L, null, 7, false)));
        String failed = service.checkInResultUrl(ServiceResult.failure(
                BizCode.CHECK_IN_NOT_REGISTERED));

        assertThat(success).isEqualTo(
                "https://web.example.com/check-in?status=success"
                        + "&departmentId=12&sessionId=30&queueNumber=7");
        assertThat(failed).isEqualTo(
                "https://web.example.com/check-in?status=failed&code=140015");
    }

    private User user(String casId, String openId) {
        User user = new User();
        user.setCasId(casId);
        user.setName("测试用户");
        user.setWechatOpenid(openId);
        return user;
    }
}
