package cn.sduonline.join.service;

import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.dto.WeChatJsSdkConfigVO;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeChatJsSdkService {

    private final WeChatJsApiTicketService ticketService;
    private final WeChatProperties properties;
    private final Clock clock;

    @Autowired
    public WeChatJsSdkService(
            WeChatJsApiTicketService ticketService,
            WeChatProperties properties) {
        this(ticketService, properties, Clock.systemUTC());
    }

    WeChatJsSdkService(
            WeChatJsApiTicketService ticketService,
            WeChatProperties properties,
            Clock clock) {
        this.ticketService = ticketService;
        this.properties = properties;
        this.clock = clock;
    }

    public WeChatJsSdkConfigVO createConfig(String pageUrl) {
        properties.validateJsSdk();
        String canonicalUrl = validateAndRemoveFragment(pageUrl);
        long timestamp = clock.instant().getEpochSecond();
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String signatureSource = "jsapi_ticket=" + ticketService.getTicket()
                + "&noncestr=" + nonceStr
                + "&timestamp=" + timestamp
                + "&url=" + canonicalUrl;

        return new WeChatJsSdkConfigVO(
                properties.getAppId(),
                timestamp,
                nonceStr,
                sha1(signatureSource),
                sanitizedTemplateIds());
    }

    private String validateAndRemoveFragment(String pageUrl) {
        if (!StringUtils.hasText(pageUrl)) {
            throw new IllegalArgumentException("页面 URL 不能为空");
        }
        int fragmentIndex = pageUrl.indexOf('#');
        String canonicalUrl = fragmentIndex < 0
                ? pageUrl : pageUrl.substring(0, fragmentIndex);
        URI uri;
        try {
            uri = URI.create(canonicalUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("页面 URL 格式无效", exception);
        }
        if (!uri.isAbsolute() || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("页面 URL 必须是完整的 HTTP(S) 地址");
        }

        String origin = originOf(uri);
        boolean allowed = properties.getJsSdkAllowedOrigins().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(this::normalizeConfiguredOrigin)
                .anyMatch(origin::equals);
        if (!allowed) {
            throw new IllegalArgumentException("该页面域名不允许生成微信 JS-SDK 签名");
        }
        return canonicalUrl;
    }

    private String normalizeConfiguredOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return originOf(uri);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "WECHAT_JS_SDK_ALLOWED_ORIGINS 包含无效地址：" + value,
                    exception);
        }
    }

    private String originOf(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port < 0
                || ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private List<String> sanitizedTemplateIds() {
        if (properties.getSubscribeTemplateIds() == null) {
            return List.of();
        }
        return properties.getSubscribeTemplateIds().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-1", exception);
        }
    }
}
