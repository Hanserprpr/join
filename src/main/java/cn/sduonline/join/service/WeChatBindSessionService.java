package cn.sduonline.join.service;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.client.WeChatApiClient.OAuthTokenResponse;
import cn.sduonline.join.client.WeChatApiException;
import cn.sduonline.join.client.WeChatNotSubscribedException;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.dto.WeChatBindSessionCreatedVO;
import cn.sduonline.join.data.dto.WeChatBindSessionStatusVO;
import cn.sduonline.join.mapper.UserMapper;
import cn.sduonline.join.data.po.User;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 普通浏览器微信绑定会话。
 *
 * <p>前端把一次性绑定链接渲染成二维码；扫码或复制链接到微信后，
 * 都通过 {@link #completeOAuth(String, String)} 完成绑定。</p>
 */
@Service
public class WeChatBindSessionService {

    private static final String SESSION_KEY_PREFIX =
            "join:wechat:bind:session:";
    private static final String CLAIM_KEY_PREFIX =
            "join:wechat:bind:claim:";
    private static final String OAUTH_STATE_KEY_PREFIX =
            "join:wechat:bind:oauth-state:";
    private static final String PENDING_SUBSCRIBE_KEY_PREFIX =
            "join:wechat:bind:pending-subscribe:";
    private static final String SCENE_PREFIX = "wb_";
    private static final String STATUS_WAITING = "WAITING";
    private static final String STATUS_BOUND = "BOUND";
    private static final String STATUS_FAILED = "FAILED";
    private static final Duration CLAIM_TTL = Duration.ofSeconds(30);
    private static final Duration RESULT_TTL = Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String FIELD_CAS_ID = "casId";
    private static final String FIELD_BROWSER_SESSION = "browserSessionId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_EXPIRES_AT = "expiresAt";

    private final WeChatApiClient apiClient;
    private final WeChatBindingService bindingService;
    private final WeChatProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final Clock clock;

    @Autowired
    public WeChatBindSessionService(
            WeChatApiClient apiClient,
            WeChatBindingService bindingService,
            WeChatProperties properties,
            StringRedisTemplate redisTemplate,
            UserMapper userMapper
    ) {
        this(
                apiClient, bindingService, properties,
                redisTemplate, userMapper, Clock.systemUTC()
        );
    }

    WeChatBindSessionService(
            WeChatApiClient apiClient,
            WeChatBindingService bindingService,
            WeChatProperties properties,
            StringRedisTemplate redisTemplate,
            UserMapper userMapper,
            Clock clock
    ) {
        this.apiClient = apiClient;
        this.bindingService = bindingService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.clock = clock;
    }

    public WeChatBindSessionCreatedVO create(
            String casId, String browserSessionId
    ) {
        properties.validateBindingSession();
        requireUserAndBrowser(casId, browserSessionId);

        String token = newSceneToken();
        int ttlSeconds = properties.getBindingSessionTtlSeconds();
        Instant expiresAt = clock.instant().plusSeconds(ttlSeconds);
        String sessionKey = sessionKey(token);
        HashOperations<String, String, String> hashes =
                redisTemplate.opsForHash();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(FIELD_CAS_ID, casId);
        values.put(FIELD_BROWSER_SESSION, browserSessionId);
        values.put(FIELD_STATUS, STATUS_WAITING);
        values.put(FIELD_EXPIRES_AT, String.valueOf(expiresAt.getEpochSecond()));
        hashes.putAll(sessionKey, values);
        redisTemplate.expire(sessionKey, Duration.ofSeconds(ttlSeconds));

        String bindingUrl = UriComponentsBuilder
                .fromUriString(properties.getBindingEntryUrl())
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
        return new WeChatBindSessionCreatedVO(
                token, STATUS_WAITING, bindingUrl,
                expiresAt, ttlSeconds
        );
    }

    public WeChatBindSessionStatusVO status(
            String token, String casId, String browserSessionId
    ) {
        BindSession session = requireSession(token);
        if (!session.casId().equals(casId)
                || !session.browserSessionId().equals(browserSessionId)) {
            throw new IllegalArgumentException("绑定会话不属于当前浏览器");
        }
        return new WeChatBindSessionStatusVO(
                session.status(),
                Math.max(0, session.expiresAt().getEpochSecond()
                        - clock.instant().getEpochSecond())
        );
    }

    /** 为复制到微信中打开的绑定入口创建一次性 OAuth state。 */
    public String createOAuthAuthorizationUrl(String token) {
        properties.validateBindingSession();
        BindSession session = requireSession(token);
        if (!STATUS_WAITING.equals(session.status())) {
            throw new IllegalArgumentException("绑定会话已经结束");
        }
        long remainingSeconds = session.expiresAt().getEpochSecond()
                - clock.instant().getEpochSecond();
        if (remainingSeconds <= 0) {
            throw new IllegalArgumentException("绑定会话无效或已过期");
        }

        String state = randomUrlToken(24);
        redisTemplate.opsForValue().set(
                OAUTH_STATE_KEY_PREFIX + state,
                token,
                Duration.ofSeconds(remainingSeconds)
        );
        return UriComponentsBuilder
                .fromUriString("https://open.weixin.qq.com/connect/oauth2/authorize")
                .queryParam("appid", properties.getAppId())
                .queryParam(
                        "redirect_uri",
                        properties.getBindingSessionOauthCallbackUrl())
                .queryParam("response_type", "code")
                .queryParam("scope", "snsapi_base")
                .queryParam("state", "qr_" + state)
                .fragment("wechat_redirect")
                .build()
                .encode()
                .toUriString();
    }

    /** 完成“复制链接到微信”后的 OAuth 绑定。 */
    public void completeOAuth(String code, String state) {
        if (!StringUtils.hasText(state) || !state.startsWith("qr_")) {
            throw new IllegalArgumentException("微信绑定状态无效");
        }
        String rawState = state.substring(3);
        String token = redisTemplate.opsForValue().getAndDelete(
                OAUTH_STATE_KEY_PREFIX + rawState);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("微信绑定链接无效或已过期");
        }
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("微信回调参数不完整");
        }
        OAuthTokenResponse response = apiClient.exchangeOAuthCode(
                properties.getAppId(), properties.getAppSecret(), code);
        if (response == null || !StringUtils.hasText(response.openid())) {
            Integer errorCode = response == null ? null : response.errcode();
            String errorMessage = response == null
                    ? "微信接口返回空响应" : response.errmsg();
            throw new WeChatApiException(
                    errorCode, "获取微信用户 OpenID 失败：" + errorMessage);
        }
        if (!bindingService.isSubscribed(response.openid())) {
            rememberPendingSubscription(token, response.openid());
            throw new WeChatNotSubscribedException("用户尚未关注公众号");
        }
        complete(token, response.openid());
        BindSession completed = findSession(token);
        if (completed == null || !STATUS_BOUND.equals(completed.status())) {
            throw new IllegalStateException("微信绑定会话未能完成");
        }
    }

    /**
     * 公众号「关注」事件推送到达时，若该 OpenID 有等待关注的绑定会话，
     * 直接完成绑定，用户无需重新扫码。
     */
    public void completeFollowUp(String openid) {
        if (!StringUtils.hasText(openid)) {
            return;
        }
        String token = redisTemplate.opsForValue()
                .getAndDelete(PENDING_SUBSCRIBE_KEY_PREFIX + openid);
        if (!StringUtils.hasText(token)) {
            return;
        }
        complete(token, openid);
    }

    /** 记录“已换到 OpenID 但尚未关注”的会话，供关注事件推送到达时续完绑定。 */
    private void rememberPendingSubscription(String token, String openid) {
        BindSession session = findSession(token);
        if (session == null) {
            return;
        }
        long remainingSeconds = session.expiresAt().getEpochSecond()
                - clock.instant().getEpochSecond();
        if (remainingSeconds <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(
                PENDING_SUBSCRIBE_KEY_PREFIX + openid,
                token,
                Duration.ofSeconds(remainingSeconds));
    }

    /** 一次性 OAuth state 消费后的幂等绑定出口。 */
    private void complete(String token, String openid) {
        if (!StringUtils.hasText(openid)) {
            throw new IllegalArgumentException("微信 OpenID 为空");
        }
        BindSession initial = findSession(token);
        if (initial == null || !STATUS_WAITING.equals(initial.status())) {
            return;
        }

        String claimKey = CLAIM_KEY_PREFIX + token;
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
                claimKey, openid, CLAIM_TTL);
        if (!Boolean.TRUE.equals(claimed)) {
            return;
        }
        try {
            BindSession current = findSession(token);
            if (current == null || !STATUS_WAITING.equals(current.status())) {
                return;
            }
            bindingService.bindOpenId(current.casId(), openid);
            HashOperations<String, String, String> hashes =
                    redisTemplate.opsForHash();
            Map<String, String> terminal = new LinkedHashMap<>();
            terminal.put(
                    FIELD_EXPIRES_AT,
                    String.valueOf(clock.instant().plus(RESULT_TTL)
                            .getEpochSecond()));
            // 状态最后写入同一个 HSET；轮询方不会观察到不完整的 BOUND。
            terminal.put(FIELD_STATUS, STATUS_BOUND);
            hashes.putAll(sessionKey(token), terminal);
            redisTemplate.expire(sessionKey(token), RESULT_TTL);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            HashOperations<String, String, String> hashes =
                    redisTemplate.opsForHash();
            hashes.putAll(sessionKey(token), Map.of(
                    FIELD_STATUS, STATUS_FAILED,
                    FIELD_EXPIRES_AT,
                    String.valueOf(clock.instant().plus(RESULT_TTL)
                            .getEpochSecond())
            ));
            redisTemplate.expire(sessionKey(token), RESULT_TTL);
            throw exception;
        } finally {
            redisTemplate.delete(claimKey);
        }
    }

    private void requireUserAndBrowser(String casId, String browserSessionId) {
        User user = StringUtils.hasText(casId)
                ? userMapper.selectById(casId) : null;
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (StringUtils.hasText(user.getWechatOpenid())) {
            throw new IllegalArgumentException("当前用户已经绑定微信");
        }
        if (!StringUtils.hasText(browserSessionId)) {
            throw new IllegalArgumentException("浏览器会话不存在");
        }
    }

    private BindSession requireSession(String token) {
        BindSession session = findSession(token);
        if (session == null) {
            throw new IllegalArgumentException("绑定会话无效或已过期");
        }
        return session;
    }

    private BindSession findSession(String token) {
        if (!StringUtils.hasText(token) || !token.startsWith(SCENE_PREFIX)) {
            return null;
        }
        Map<String, String> values = redisTemplate.<String, String>opsForHash()
                .entries(sessionKey(token));
        if (values.isEmpty()) {
            return null;
        }
        try {
            return new BindSession(
                    values.get(FIELD_CAS_ID),
                    values.get(FIELD_BROWSER_SESSION),
                    values.get(FIELD_STATUS),
                    Instant.ofEpochSecond(Long.parseLong(
                            values.get(FIELD_EXPIRES_AT)))
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String sessionKey(String token) {
        return SESSION_KEY_PREFIX + token;
    }

    private static String newSceneToken() {
        return SCENE_PREFIX + randomUrlToken(18);
    }

    private static String randomUrlToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record BindSession(
            String casId,
            String browserSessionId,
            String status,
            Instant expiresAt
    ) {
    }
}
