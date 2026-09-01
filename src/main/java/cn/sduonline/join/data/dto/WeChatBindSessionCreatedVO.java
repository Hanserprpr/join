package cn.sduonline.join.data.dto;

import java.time.Instant;

/** 普通浏览器创建微信扫码绑定会话后的展示数据。 */
public record WeChatBindSessionCreatedVO(
        String sessionId,
        String status,
        String bindingUrl,
        Instant expiresAt,
        long expiresIn
) {
}
