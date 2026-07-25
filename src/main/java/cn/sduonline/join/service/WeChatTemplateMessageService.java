package cn.sduonline.join.service;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.client.WeChatApiClient.MiniProgram;
import cn.sduonline.join.client.WeChatApiClient.TemplateData;
import cn.sduonline.join.client.WeChatApiClient.TemplateMessageRequest;
import cn.sduonline.join.client.WeChatApiClient.TemplateSendResponse;
import cn.sduonline.join.client.WeChatApiException;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WeChatTemplateMessageService {

    private static final int INVALID_ACCESS_TOKEN = 40014;
    private static final int EXPIRED_ACCESS_TOKEN = 42001;

    private final WeChatApiClient apiClient;
    private final WeChatAccessTokenService tokenService;

    public WeChatTemplateMessageService(
            WeChatApiClient apiClient,
            WeChatAccessTokenService tokenService) {
        this.apiClient = apiClient;
        this.tokenService = tokenService;
    }

    public long send(
            String openId,
            String templateId,
            Map<String, TemplateData> data) {
        return send(openId, templateId, null, null, data);
    }

    public long send(
            String openId,
            String templateId,
            String url,
            MiniProgram miniProgram,
            Map<String, TemplateData> data) {
        requireText(openId, "openId");
        requireText(templateId, "templateId");
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("模板消息 data 不能为空");
        }

        TemplateMessageRequest request =
                new TemplateMessageRequest(openId, templateId, url, miniProgram, data);
        TemplateSendResponse response =
                apiClient.sendTemplateMessage(tokenService.getAccessToken(), request);

        if (hasTokenError(response)) {
            response = apiClient.sendTemplateMessage(tokenService.forceRefresh(), request);
        }
        if (response == null || response.errcode() == null || response.errcode() != 0) {
            Integer code = response == null ? null : response.errcode();
            String message = response == null ? "微信接口返回空响应" : response.errmsg();
            throw new WeChatApiException(code, "发送微信模板消息失败：" + message);
        }
        if (response.messageId() == null) {
            throw new WeChatApiException("微信接口未返回 msgid");
        }
        return response.messageId();
    }

    private boolean hasTokenError(TemplateSendResponse response) {
        return response != null
                && (response.errcode() != null)
                && (response.errcode() == INVALID_ACCESS_TOKEN
                || response.errcode() == EXPIRED_ACCESS_TOKEN);
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
