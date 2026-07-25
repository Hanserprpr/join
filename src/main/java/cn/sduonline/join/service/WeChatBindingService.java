package cn.sduonline.join.service;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.client.WeChatApiClient.TemplateData;
import cn.sduonline.join.client.WeChatApiException;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeChatBindingService {

    private static final String STATE_KEY_PREFIX = "join:wechat:binding:state:";
    private static final DateTimeFormatter BINDING_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WeChatApiClient apiClient;
    private final WeChatProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final WeChatTemplateMessageService templateMessageService;

    public String createAuthorizationUrl(String casId) {
        properties.validateBinding();
        if (userMapper.selectById(casId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        String state = UUID.randomUUID().toString().replace("-", "");
        long ttl = Math.max(60, properties.getBindingStateTtlSeconds());
        redisTemplate.opsForValue().set(
                STATE_KEY_PREFIX + state, casId, Duration.ofSeconds(ttl));

        return UriComponentsBuilder
                .fromUriString("https://open.weixin.qq.com/connect/oauth2/authorize")
                .queryParam("appid", properties.getAppId())
                .queryParam("redirect_uri", properties.getOauthCallbackUrl())
                .queryParam("response_type", "code")
                .queryParam("scope", "snsapi_base")
                .queryParam("state", state)
                .fragment("wechat_redirect")
                .build()
                .encode()
                .toUriString();
    }

    @Transactional
    public void completeBinding(String code, String state) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new IllegalArgumentException("微信回调参数不完整");
        }

        String casId = redisTemplate.opsForValue()
                .getAndDelete(STATE_KEY_PREFIX + state);
        if (!StringUtils.hasText(casId)) {
            throw new IllegalArgumentException("绑定链接无效或已过期");
        }

        properties.validateBinding();
        WeChatApiClient.OAuthTokenResponse response = apiClient.exchangeOAuthCode(
                properties.getAppId(), properties.getAppSecret(), code);
        if (response == null || !StringUtils.hasText(response.openid())) {
            Integer errorCode = response == null ? null : response.errcode();
            String errorMessage = response == null ? "微信接口返回空响应" : response.errmsg();
            throw new WeChatApiException(
                    errorCode, "获取微信用户 OpenID 失败：" + errorMessage);
        }

        User user = userMapper.selectById(casId);
        if (user == null) {
            throw new IllegalArgumentException("绑定用户不存在");
        }

        User owner = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getWechatOpenid, response.openid()));
        if (owner != null && !owner.getCasId().equals(casId)) {
            throw new IllegalStateException("该微信已经绑定其他用户");
        }

        user.setWechatOpenid(response.openid());
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("该微信已经绑定其他用户", exception);
        }

        sendBindingSuccessMessage(user);
        
    }

    public boolean isBound(String casId) {
        User user = userMapper.selectById(casId);
        return user != null && StringUtils.hasText(user.getWechatOpenid());
    }

    @Transactional
    public void unbind(String casId) {
        User user = userMapper.selectById(casId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setWechatOpenid(null);
        userMapper.updateById(user);
    }

    public String bindingResultUrl(boolean success) {
        return UriComponentsBuilder.fromUriString(properties.getBindingResultUrl())
                .queryParam("status", success ? "success" : "failed")
                .build()
                .encode()
                .toUriString();
    }

    /**
     * 绑定已经落库后发送通知。通知属于附加动作，发送失败不应撤销绑定。
     */
    private void sendBindingSuccessMessage(User user) {
        if (!StringUtils.hasText(properties.getBindingTemplateId())) {
            log.info("WeChat binding template ID is empty; skip notification");
            return;
        }
        try {
            templateMessageService.send(
                    user.getWechatOpenid(),
                    properties.getBindingTemplateId(),
                    Map.of(
                            "thing2", new TemplateData(user.getName()),
                            "thing9", new TemplateData(properties.getSystemName()),
                            "time4", new TemplateData(
                                    LocalDateTime.now().format(BINDING_TIME_FORMAT))
                    ));
        } catch (RuntimeException exception) {
            log.warn(
                    "WeChat binding succeeded but notification failed, casId={}, reason={}",
                    user.getCasId(),
                    exception.getMessage());
        }
    }
}
