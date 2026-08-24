package cn.sduonline.join.service.avatar;

import cn.sduonline.join.data.enums.BizCode;

/** ClamAV 检出恶意文件或扫描服务不可用。 */
public class ClamAvScanException extends RuntimeException {

    private final BizCode bizCode;

    public ClamAvScanException(BizCode bizCode, String message) {
        super(message);
        this.bizCode = bizCode;
    }

    public ClamAvScanException(BizCode bizCode, String message, Throwable cause) {
        super(message, cause);
        this.bizCode = bizCode;
    }

    public BizCode getBizCode() {
        return bizCode;
    }
}
