package cn.sduonline.join.client;

/** 用户尚未关注公众号，无法通过静默 OAuth 判定绑定应转向关注引导页。 */
public class WeChatNotSubscribedException extends RuntimeException {

    public WeChatNotSubscribedException(String message) {
        super(message);
    }
}
