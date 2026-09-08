package cn.sduonline.join.service;

import cn.sduonline.join.client.WeChatApiClient.TemplateData;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.dto.AdmissionWeChatRecipient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** 发布录取后发送微信面试结果通知。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionWeChatNotificationService {

    private static final String RESULT_ADMITTED = "录取";

    private final WeChatTemplateMessageService templateMessageService;
    private final WeChatProperties properties;

    /**
     * 只在录取事务成功提交后发送。微信属于附加通知，任何单条失败都不回滚业务结果。
     */
    public void sendAfterCommit(
            String departmentName,
            List<AdmissionWeChatRecipient> recipients,
            Set<Long> admittedApplicationIds
    ) {
        if (recipients == null || recipients.isEmpty()
                || !StringUtils.hasText(
                        properties.getAdmissionResultTemplateId())) {
            return;
        }
        String normalizedDepartmentName = StringUtils.hasText(departmentName)
                ? departmentName.trim() : "部门";
        List<AdmissionWeChatRecipient> snapshot = List.copyOf(recipients);
        Set<Long> admittedSnapshot = Set.copyOf(admittedApplicationIds);
        Runnable action = () -> sendAll(
                normalizedDepartmentName, snapshot, admittedSnapshot);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    }
            );
        } else {
            action.run();
        }
    }

    private void sendAll(
            String departmentName,
            List<AdmissionWeChatRecipient> recipients,
            Set<Long> admittedApplicationIds
    ) {
        for (AdmissionWeChatRecipient recipient : recipients) {
            if (!admittedApplicationIds.contains(recipient.applicationId())
                    || !StringUtils.hasText(recipient.wechatOpenid())) {
                continue;
            }
            try {
                templateMessageService.send(
                        recipient.wechatOpenid(),
                        properties.getAdmissionResultTemplateId(),
                        Map.of(
                                "thing1", new TemplateData(
                                        normalizedName(recipient)),
                                "const2", new TemplateData(RESULT_ADMITTED),
                                "thing12", new TemplateData(departmentName)
                        )
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Failed to send admission result WeChat notification: "
                                + "applicationId={}, result={}",
                        recipient.applicationId(), RESULT_ADMITTED, exception
                );
            }
        }
    }

    private static String normalizedName(AdmissionWeChatRecipient recipient) {
        return StringUtils.hasText(recipient.applicantName())
                ? recipient.applicantName().trim() : "同学";
    }
}
