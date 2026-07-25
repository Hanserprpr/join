package cn.sduonline.join.security;

/**
 * 可信身份系统暂时不可用。它与“Token 无效”不同，应映射为 503。
 */
public class ExternalIdentityUnavailableException extends RuntimeException {

    public ExternalIdentityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
