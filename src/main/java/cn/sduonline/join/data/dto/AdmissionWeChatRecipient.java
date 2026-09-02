package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.ApplicationStatus;

/** 发布录取结果时需要发送微信通知的报名人。 */
public record AdmissionWeChatRecipient(
        Long applicationId,
        String applicantName,
        String wechatOpenid,
        ApplicationStatus status
) {
}
