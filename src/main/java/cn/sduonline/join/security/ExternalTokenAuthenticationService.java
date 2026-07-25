package cn.sduonline.join.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.sduonline.join.client.ExternalIdentityClient;
import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.ExternalIdentityResponse;
import cn.sduonline.join.data.dto.ExternalStudentIdentity;
import cn.sduonline.join.service.UserService;
import feign.FeignException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 将外部 Token 解析为可信学生身份，并用 Token 摘要缓存结果。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalTokenAuthenticationService {

    private static final String CACHE_PREFIX = "auth:external:";

    private final StringRedisTemplate redisTemplate;
    private final ExternalIdentityClient externalIdentityClient;
    private final AppProperties appProperties;
    private final UserService userService;

    public ExternalStudentIdentity authenticate(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw NotLoginException.newInstance(
                    "external", null, "未提供 Token", NotLoginException.NOT_TOKEN);
        }

        String cacheKey = CACHE_PREFIX + sha256(rawToken);
        ExternalStudentIdentity cached = readCache(cacheKey);
        if (cached != null) {
            userService.syncFromExternal(cached);
            return cached;
        }

        ExternalStudentIdentity identity;
        try {
            ExternalIdentityResponse response =
                    externalIdentityClient.getCurrentStudent(rawToken);
            identity = mapIdentity(response);
        } catch (FeignException.Unauthorized | FeignException.Forbidden ex) {
            log.warn(
                    "External identity rejected token, status={}, response={}",
                    ex.status(),
                    abbreviatedBody(ex)
            );
            throw NotLoginException.newInstance(
                    "external", rawToken, "Token 无效或已过期",
                    NotLoginException.INVALID_TOKEN);
        } catch (FeignException ex) {
            log.error(
                    "External identity request failed, status={}, response={}",
                    ex.status(),
                    abbreviatedBody(ex),
                    ex
            );
            throw new ExternalIdentityUnavailableException("外部身份服务暂时不可用", ex);
        }

        validate(identity, rawToken);
        userService.syncFromExternal(identity);
        writeCache(cacheKey, identity);
        return identity;
    }

    private ExternalStudentIdentity readCache(String key) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key);
        if (values.isEmpty()) {
            return null;
        }
        return new ExternalStudentIdentity(
                (String) values.get("studentNumber"),
                (String) values.get("name"),
                (String) values.get("college"),
                (String) values.get("major")
        );
    }

    private void writeCache(String key, ExternalStudentIdentity identity) {
        redisTemplate.opsForHash().putAll(key, Map.of(
                "studentNumber", identity.studentNumber(),
                "name", identity.name(),
                "college", identity.college(),
                "major", identity.major()
        ));
        redisTemplate.expire(
                key,
                Duration.ofSeconds(appProperties.getExternalIdentity().getCacheTtlSeconds())
        );
    }

    private void validate(ExternalStudentIdentity identity, String rawToken) {
        if (identity == null
                || !StringUtils.hasText(identity.studentNumber())
                || !StringUtils.hasText(identity.name())
                || !StringUtils.hasText(identity.college())
                || !StringUtils.hasText(identity.major())) {
            log.warn(
                    "External identity response is incomplete or does not match DTO: {}",
                    identity
            );
            throw NotLoginException.newInstance(
                    "external", rawToken, "外部身份信息不完整",
                    NotLoginException.INVALID_TOKEN);
        }
    }

    private ExternalStudentIdentity mapIdentity(
            ExternalIdentityResponse response
    ) {
        if (response == null) {
            return null;
        }

        if (response.code() == null || response.code() != 0) {
            log.warn(
                    "External identity returned business failure, code={}, message={}",
                    response.code(),
                    response.message()
            );
            return null;
        }

        return response.data() == null
                ? null
                : response.data().toStudentIdentity();
    }

    private static String abbreviatedBody(FeignException exception) {
        String body = exception.contentUTF8();
        if (!StringUtils.hasText(body)) {
            return "<empty>";
        }
        String singleLine = body.replaceAll("[\\r\\n]+", " ");
        return singleLine.length() <= 500
                ? singleLine
                : singleLine.substring(0, 500) + "...";
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", ex);
        }
    }
}
