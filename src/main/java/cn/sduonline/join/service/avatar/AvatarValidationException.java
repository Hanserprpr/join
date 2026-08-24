package cn.sduonline.join.service.avatar;

import cn.sduonline.join.data.enums.BizCode;

/** 头像上传参数校验失败。 */
public class AvatarValidationException extends RuntimeException {

    private final BizCode bizCode;

    public AvatarValidationException(BizCode bizCode) {
        super(bizCode.getMsg());
        this.bizCode = bizCode;
    }

    public BizCode getBizCode() {
        return bizCode;
    }
}
