package cn.sduonline.join.config;

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
            "gmRpMS8vWvQuR02-tcVMpyuOC3sLafkpErxPfAFE1DQ";
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
