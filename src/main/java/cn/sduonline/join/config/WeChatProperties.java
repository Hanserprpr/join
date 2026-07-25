package cn.sduonline.join.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
    private String systemName = "学生在线纳新系统";
    private long tokenRefreshAheadSeconds = 300;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public long getTokenRefreshAheadSeconds() {
        return tokenRefreshAheadSeconds;
    }

    public String getOauthCallbackUrl() {
        return oauthCallbackUrl;
    }

    public void setOauthCallbackUrl(String oauthCallbackUrl) {
        this.oauthCallbackUrl = oauthCallbackUrl;
    }

    public String getBindingResultUrl() {
        return bindingResultUrl;
    }

    public void setBindingResultUrl(String bindingResultUrl) {
        this.bindingResultUrl = bindingResultUrl;
    }

    public long getBindingStateTtlSeconds() {
        return bindingStateTtlSeconds;
    }

    public void setBindingStateTtlSeconds(long bindingStateTtlSeconds) {
        this.bindingStateTtlSeconds = bindingStateTtlSeconds;
    }

    public String getBindingTemplateId() {
        return bindingTemplateId;
    }

    public void setBindingTemplateId(String bindingTemplateId) {
        this.bindingTemplateId = bindingTemplateId;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public void setTokenRefreshAheadSeconds(long tokenRefreshAheadSeconds) {
        this.tokenRefreshAheadSeconds = tokenRefreshAheadSeconds;
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
}
