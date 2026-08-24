package cn.sduonline.join.data.dto;

import java.util.List;

public record WeChatJsSdkConfigVO(
        String appId,
        long timestamp,
        String nonceStr,
        String signature,
        List<String> templateIds) {
}
