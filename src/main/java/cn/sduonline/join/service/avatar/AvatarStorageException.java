package cn.sduonline.join.service.avatar;

/** 对象存储请求失败。 */
public class AvatarStorageException extends RuntimeException {

    public AvatarStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
