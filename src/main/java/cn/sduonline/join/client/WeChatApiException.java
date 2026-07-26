package cn.sduonline.join.client;

import lombok.Getter;

@Getter
public class WeChatApiException extends RuntimeException {

    private final Integer errorCode;

    public WeChatApiException(String message) {
        this(null, message);
    }

    public WeChatApiException(Integer errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}
