package cn.sduonline.join.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.wechat")
public class WeChatProperties {

    private String appId = "";
    private String appSecret = "";
    private String oauthCallbackUrl =
            "http://localhost:8080/api/wechat/binding/callback";
    private String bindingResultUrl =
            "http://localhost:5173/wechat-binding";
    private long bindingStateTtlSeconds = 600;
    private String bindingTemplateId =
            "Tw8ThFuRhgMOkrTirVHRnTPbxFdxyaFo2PIJdFqkxQw";
    private String interviewCallTemplateId =
            "79icrV3YXPf6_FRxqDLgzGsyW1QNU-X-1IFk1j48LKw";
    /** 前端一次发起订阅时展示的订阅通知模板 ID。 */
    private List<String> subscribeTemplateIds = new ArrayList<>();
    /** 允许生成 JS-SDK 签名的前端 Origin。 */
    private List<String> jsSdkAllowedOrigins = new ArrayList<>();
    private long ticketRefreshAheadSeconds = 300;
    private String systemName = "学生在线纳新系统";
    private long tokenRefreshAheadSeconds = 300;
    private Proxy proxy = new Proxy();

    @Getter
    @Setter
    public static class Proxy {
        /** 是否让所有微信 API 请求经过 HTTP CONNECT 代理。 */
        private boolean enabled = false;
        private String host = "202.194.20.230";
        private int port = 8080;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;
    }

    public void validate() {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException(
                    "微信配置缺失，请设置 WECHAT_APP_ID 和 WECHAT_APP_SECRET");
        }
    }

    public void validateBinding() {
        validate();
        if (oauthCallbackUrl == null || oauthCallbackUrl.isBlank()
                || bindingResultUrl == null || bindingResultUrl.isBlank()) {
            throw new IllegalStateException(
                    "微信绑定配置缺失，请设置 WECHAT_OAUTH_CALLBACK_URL 和 "
                            + "WECHAT_BINDING_RESULT_URL");
        }
    }

    public void validateJsSdk() {
        validate();
        if (jsSdkAllowedOrigins == null || jsSdkAllowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "微信 JS-SDK 允许域名未配置，请设置 WECHAT_JS_SDK_ALLOWED_ORIGINS");
        }
    }

    public void validateProxy() {
        if (!proxy.isEnabled()) {
            return;
        }
        if (proxy.getHost() == null || proxy.getHost().isBlank()
                || proxy.getPort() < 1 || proxy.getPort() > 65535
                || proxy.getConnectTimeoutMs() < 1 || proxy.getReadTimeoutMs() < 1) {
            throw new IllegalStateException(
                    "微信代理配置无效，请检查 WECHAT_PROXY_HOST、"
                            + "WECHAT_PROXY_PORT 和超时配置"
            );
        }
    }
}
