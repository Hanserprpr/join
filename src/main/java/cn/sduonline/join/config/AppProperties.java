package cn.sduonline.join.config;

import java.util.Arrays;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用自定义配置（前缀 {@code app}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * OIDC 登录成功后回跳的前端地址。
     */
    private String frontendUrl = "http://localhost:5173";

    /**
     * 允许的跨域来源，多个用英文逗号分隔。
     */
    private String corsAllowedOrigins = "http://localhost:5173";

    private ExternalIdentity externalIdentity = new ExternalIdentity();
    private CheckIn checkIn = new CheckIn();

    @Data
    public static class CheckIn {
        /** 动态签到二维码的有效时间。 */
        private long tokenTtlSeconds = 30;
        /** 前端刷新动态二维码的建议间隔。 */
        private long qrRefreshSeconds = 8;
    }

    @Data
    public static class ExternalIdentity {

        /**
         * 外部用户身份接口服务地址。
         */
        private String baseUrl = "https://i.sdu.edu.cn";

        /**
         * Token 对应身份在 Redis 中的缓存秒数。
         */
        private long cacheTtlSeconds = 60;
    }

    /**
     * 解析 {@link #corsAllowedOrigins} 为列表。
     */
    public List<String> getCorsAllowedOriginList() {
        return Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
