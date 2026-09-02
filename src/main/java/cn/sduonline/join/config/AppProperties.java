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
    private String frontendUrl = "https://i.sdu.edu.cn/recruit";

    /**
     * 允许的跨域来源，多个用英文逗号分隔。
     */
    private String corsAllowedOrigins = "https://i.sdu.edu.cn";

    private ExternalIdentity externalIdentity = new ExternalIdentity();
    private CheckIn checkIn = new CheckIn();
    private Avatar avatar = new Avatar();
    private Clamav clamav = new Clamav();
    private Poster poster = new Poster();
    private CollegeMajors collegeMajors = new CollegeMajors();

    @Data
    public static class CollegeMajors {
        private Nacos nacos = new Nacos();
    }

    @Data
    public static class Nacos {
        /** 关闭时只使用 classpath 中的降级字典。 */
        private boolean enabled = true;
        private String serverAddr = "127.0.0.1:8848";
        /** Nacos 客户端需要命名空间 ID，不是显示名称。 */
        private String namespace = "";
        private String dataId = "college-majors";
        private String group = "DEFAULT_GROUP";
        private String username = "";
        private String password = "";
        private long timeoutMs = 5000;
        private long retryDelayMs = 30000;
    }

    @Data
    public static class Avatar {
        /** 存储后端：local 或 s3。 */
        private String storage = "local";
        /** 本地存储目录；生产环境建议替换为对象存储实现。 */
        private String localDirectory = "./data/avatars";
        /** 头像公网基地址，为空时根据当前请求生成。 */
        private String publicBaseUrl = "";
        /** 单个头像最大字节数，默认 2 MiB。 */
        private long maxSizeBytes = 2 * 1024 * 1024;
        private S3 s3 = new S3();
    }

    @Data
    public static class S3 {
        /** S3 兼容服务地址，例如 http://minio:9000。 */
        private String endpoint = "";
        /** 签名区域；自建服务通常可使用 us-east-1。 */
        private String region = "us-east-1";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "";
        /** 对外访问对象的基地址，例如 https://cdn.example.com/bucket。 */
        private String publicBaseUrl = "";
        /** MinIO 等服务通常需要 path-style。 */
        private boolean pathStyleAccess = true;
        /** 头像预签名读取 URL 的有效秒数，最长 7 天。 */
        private long presignedUrlTtlSeconds = 86400;
    }

    @Data
    public static class Clamav {
        /** 是否在头像落盘/入桶前执行病毒扫描。 */
        private boolean enabled = false;
        private String host = "127.0.0.1";
        private int port = 3310;
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 5000;
    }

    @Data
    public static class Poster {
        /** 本地存储目录。 */
        private String localDirectory = "./data/posters";
        /** 本地存储时的公网基地址，为空时根据请求生成。 */
        private String publicBaseUrl = "";
        /** 单张海报最大字节数，默认 10 MiB。 */
        private long maxSizeBytes = 10 * 1024 * 1024;
        /** S3 海报预签名读取 URL 的有效秒数，最长 7 天。 */
        private long presignedUrlTtlSeconds = 86400;
        /** 额外允许保存的海报 URL 前缀，多个用英文逗号分隔。 */
        private String allowedUrlPrefixes = "";

        public List<String> getAllowedUrlPrefixList() {
            return Arrays.stream(allowedUrlPrefixes.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    @Data
    public static class CheckIn {
        /** 动态签到二维码的有效时间。 */
        private long tokenTtlSeconds = 8;
        /** 微信扫码时先进入的后端公网地址。 */
        private String entryUrl =
                "https://i.sdu.edu.cn/recruit/api/wechat/check-in/entry";
        /** 签到或微信登录完成后的前端结果页。 */
        private String resultUrl = "https://i.sdu.edu.cn/recruit/check-in";
        /** 扫码后允许完成微信 OAuth 的最长时间。 */
        private long oauthStateTtlSeconds = 120;
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
