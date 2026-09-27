package cn.sduonline.join.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cn.sduonline.join.client.WeChatApiClient.TemplateData;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.dto.AdmissionWeChatRecipient;
import cn.sduonline.join.data.enums.ApplicationStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdmissionWeChatNotificationServiceTest {

    private static final String TEMPLATE_ID =
            "test-admission-template";

    private WeChatTemplateMessageService templateMessageService;
    private AdmissionWeChatNotificationService service;

    @BeforeEach
    void setUp() {
        templateMessageService = mock(WeChatTemplateMessageService.class);
        WeChatProperties properties = new WeChatProperties();
        properties.setAdmissionResultTemplateId(TEMPLATE_ID);
        service = new AdmissionWeChatNotificationService(
                templateMessageService, properties);
    }

    @Test
    void sendsOnlyAdmittedNotificationsAndSkipsRejectedRecipients() {
        AdmissionWeChatRecipient admitted = recipient(
                100L, "张三", "openid-admitted",
                ApplicationStatus.ADMISSION_DRAFT);
        AdmissionWeChatRecipient rejected = recipient(
                101L, "李四", "openid-rejected",
                ApplicationStatus.SUBMITTED);

        service.sendAfterCommit(
                "技术部", List.of(admitted, rejected), Set.of(100L));

        verify(templateMessageService).send(
                "openid-admitted", TEMPLATE_ID,
                Map.of(
                        "thing1", new TemplateData("张三"),
                        "const2", new TemplateData("录取"),
                        "thing12", new TemplateData("技术部")
                ));
        verifyNoMoreInteractions(templateMessageService);
    }

    @Test
    void oneWechatFailureDoesNotPreventRemainingNotifications() {
        AdmissionWeChatRecipient first = recipient(
                100L, "张三", "openid-1",
                ApplicationStatus.ADMISSION_DRAFT);
        AdmissionWeChatRecipient second = recipient(
                101L, "李四", "openid-2",
                ApplicationStatus.ADMISSION_DRAFT);
        when(templateMessageService.send(
                eq("openid-1"), eq(TEMPLATE_ID), any()))
                .thenThrow(new IllegalStateException("wechat unavailable"));

        service.sendAfterCommit(
                "技术部", List.of(first, second), Set.of(100L, 101L));

        verify(templateMessageService).send(
                eq("openid-2"), eq(TEMPLATE_ID), any());
    }

    private static AdmissionWeChatRecipient recipient(
            Long applicationId,
            String name,
            String openid,
            ApplicationStatus status
    ) {
        return new AdmissionWeChatRecipient(
                applicationId, name, openid, status);
    }
}
