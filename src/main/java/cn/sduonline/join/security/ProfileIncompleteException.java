package cn.sduonline.join.security;

/**
 * 当前登录用户尚未完成使用业务功能所需的个人资料。
 */
public class ProfileIncompleteException extends RuntimeException {

    public ProfileIncompleteException() {
        super("用户资料未完成");
    }
}
