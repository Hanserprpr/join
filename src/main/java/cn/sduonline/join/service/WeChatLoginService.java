package cn.sduonline.join.service;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.client.WeChatApiException;
import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.dto.CheckInQrGrant;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/** 微信网页授权登录：已绑定直接建立登录态，未绑定转统一认证。 */
@Service
@RequiredArgsConstructor
public class WeChatLoginService {

    private static final String STATE_KEY_PREFIX = "join:wechat:login:state:";
    private static final String UNIFIED_AUTH_PATH =
            "/api/oauth2/authorization/sdu";
    private static final String LOGIN_STATE = "login";
    private static final String CHECK_IN_STATE_PREFIX = "check-in:";

    private final WeChatApiClient apiClient;
    private final WeChatProperties properties;
    private final AppProperties appProperties;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    public String createAuthorizationUrl() {
        return createAuthorizationUrl(
                LOGIN_STATE, properties.getLoginStateTtlSeconds());
    }

    public String createCheckInAuthorizationUrl(CheckInQrGrant grant) {
        if (grant == null || grant.departmentId() == null
                || grant.sessionId() == null) {
            throw new IllegalArgumentException("签到凭证不完整");
        }
        return createAuthorizationUrl(
                CHECK_IN_STATE_PREFIX + grant.departmentId()
                        + ":" + grant.sessionId(),
                appProperties.getCheckIn().getOauthStateTtlSeconds());
    }

    private String createAuthorizationUrl(String stateValue, long ttlSeconds) {
        properties.validateLogin();

        String state = UUID.randomUUID().toString().replace("-", "");
        long ttl = Math.max(60, ttlSeconds);
        redisTemplate.opsForValue().set(
                STATE_KEY_PREFIX + state, stateValue, Duration.ofSeconds(ttl));

        return UriComponentsBuilder
                .fromUriString("https://open.weixin.qq.com/connect/oauth2/authorize")
                .queryParam("appid", properties.getAppId())
                .queryParam("redirect_uri", properties.getLoginOauthCallbackUrl())
                .queryParam("response_type", "code")
                .queryParam("scope", "snsapi_base")
                .queryParam("state", state)
                .fragment("wechat_redirect")
                .build()
                .encode()
                .toUriString();
    }

    /**
     * 统一认证登录地址。微信授权后发现 OpenID 未绑定时跳到这里补登录。
     *
     * @param contextPath 反代还原后的应用上下文路径
     */
    public String unifiedAuthUrl(String contextPath) {
        return (contextPath == null ? "" : contextPath) + UNIFIED_AUTH_PATH;
    }

    /**
     * 消费一次性 state，并解析该 OpenID 对应的本地用户。
     * OpenID 尚未绑定时 {@code user} 为空，由调用方转统一认证。
     */
    public AuthenticationResult authenticate(String code, String state) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new IllegalArgumentException("微信回调参数不完整");
        }

        String stateValue = redisTemplate.opsForValue()
                .getAndDelete(STATE_KEY_PREFIX + state);
        if (!StringUtils.hasText(stateValue)) {
            throw new IllegalArgumentException("微信登录链接无效或已过期");
        }

        properties.validateLogin();
        WeChatApiClient.OAuthTokenResponse response = apiClient.exchangeOAuthCode(
                properties.getAppId(), properties.getAppSecret(), code);
        if (response == null || !StringUtils.hasText(response.openid())) {
            Integer errorCode = response == null ? null : response.errcode();
            String errorMessage = response == null
                    ? "微信接口返回空响应" : response.errmsg();
            throw new WeChatApiException(
                    errorCode, "获取微信用户 OpenID 失败：" + errorMessage);
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getWechatOpenid, response.openid()));
        return new AuthenticationResult(
                user, response.openid(), parseCheckInGrant(stateValue));
    }

    public String loginResultUrl(boolean success) {
        return UriComponentsBuilder.fromUriString(properties.getLoginResultUrl())
                .queryParam("login", success ? "success" : "failed")
                .queryParam("source", "wechat")
                .build()
                .encode()
                .toUriString();
    }

    public String checkInResultUrl(ServiceResult<CheckInVO> result) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(appProperties.getCheckIn().getResultUrl())
                .queryParam("status", result.isSuccess()
                        ? "success" : "failed");
        if (result.isSuccess()) {
            CheckInVO data = result.data();
            builder.queryParam("departmentId", data.departmentId())
                    .queryParam("sessionId", data.sessionId())
                    .queryParam("queueNumber", data.queueNumber());
        } else {
            builder.queryParam("code", result.error().getCode());
        }
        return builder.build().encode().toUriString();
    }

    private CheckInQrGrant parseCheckInGrant(String stateValue) {
        if (LOGIN_STATE.equals(stateValue)) {
            return null;
        }
        if (!stateValue.startsWith(CHECK_IN_STATE_PREFIX)) {
            throw new IllegalArgumentException("微信登录状态无效");
        }
        try {
            String[] parts = stateValue.substring(
                    CHECK_IN_STATE_PREFIX.length()).split(":", 2);
            return new CheckInQrGrant(
                    Long.valueOf(parts[0]), Long.valueOf(parts[1]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("微信签到状态无效", exception);
        }
    }

    /**
     * @param user 已绑定该 OpenID 的本地用户，未绑定时为 {@code null}
     */
    public record AuthenticationResult(
            User user,
            String openid,
            CheckInQrGrant checkInGrant
    ) {
    }
}
