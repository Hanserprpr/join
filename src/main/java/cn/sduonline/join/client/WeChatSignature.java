package cn.sduonline.join.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.springframework.util.StringUtils;

/** 校验公众号服务器配置推送请求的签名（token、timestamp、nonce 字典序拼接后取 SHA-1）。 */
public final class WeChatSignature {

    private WeChatSignature() {
    }

    public static boolean matches(
            String token, String signature, String timestamp, String nonce) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(signature)
                || !StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            return false;
        }
        String[] parts = {token, timestamp, nonce};
        Arrays.sort(parts);
        String joined = parts[0] + parts[1] + parts[2];
        return signature.equalsIgnoreCase(sha1Hex(joined));
    }

    private static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 不可用", exception);
        }
    }
}
