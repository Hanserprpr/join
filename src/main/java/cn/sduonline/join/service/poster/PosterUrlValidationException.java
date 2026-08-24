package cn.sduonline.join.service.poster;

/** 海报 URL 不属于可信白名单。 */
public class PosterUrlValidationException extends RuntimeException {

    public PosterUrlValidationException() {
        super("海报地址不在允许的白名单中");
    }
}
