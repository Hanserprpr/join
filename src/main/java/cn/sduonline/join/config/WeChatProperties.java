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
            "";
    private String interviewCallTemplateId =
            "gmRpMS8vWvQuR02-tcVMp-lBRyH_qKelfziA3yKiRyY";
    private String systemName = "学生在线纳新系统";
    private long tokenRefreshAheadSeconds = 300;

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
