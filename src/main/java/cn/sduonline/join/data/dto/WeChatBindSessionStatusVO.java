package cn.sduonline.join.data.dto;

/** 浏览器轮询得到的微信扫码绑定结果。 */
public record WeChatBindSessionStatusVO(
        String status,
        long expiresIn
) {
}
