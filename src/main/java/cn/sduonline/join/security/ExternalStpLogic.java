package cn.sduonline.join.security;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.context.SaHolder;
import cn.sduonline.join.data.dto.ExternalStudentIdentity;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 统一登录逻辑。
 * <p>
 * 外部 Token 身份只存在于当前请求；没有外部身份时回退到 Sa-Token
 * 原生登录态，因此 OIDC 和外部 Token 在业务层都可以使用 {@link StpUtil}。
 */
@Component
public class ExternalStpLogic extends StpLogic {

    private static final String STORAGE_IDENTITY_KEY = "external-student-identity";

    private final ExternalTokenAuthenticationService authenticationService;

    public ExternalStpLogic(ExternalTokenAuthenticationService authenticationService) {
        super(StpUtil.TYPE);
        this.authenticationService = authenticationService;
    }

    /**
     * 替换 {@link StpUtil} 默认 login 逻辑，使 {@code @SaCheckLogin} 与业务代码
     * 同时兼容 OIDC Cookie 和外部 Token。
     * <p>
     * 必须调用 {@link StpUtil#setStpLogic(StpLogic)}；只写入
     * {@link cn.dev33.satoken.SaManager} 不会替换 {@code @SaCheckLogin}
     * 实际使用的默认实例。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAsDefaultStpLogic() {
        StpUtil.setStpLogic(this);
    }

    /**
     * 解析 Token 并把身份放入 Sa-Token 当前请求上下文。
     */
    public ExternalStudentIdentity authenticate(String rawToken) {
        ExternalStudentIdentity identity = authenticationService.authenticate(rawToken);
        SaHolder.getStorage().set(STORAGE_IDENTITY_KEY, identity);
        return identity;
    }

    @Override
    public Object getLoginIdDefaultNull() {
        ExternalStudentIdentity identity = getCurrentIdentity();
        return identity == null
                ? super.getLoginIdDefaultNull()
                : identity.studentNumber();
    }

    @Override
    public Object getLoginId() {
        ExternalStudentIdentity identity = getCurrentIdentity();
        return identity == null
                ? super.getLoginId()
                : identity.studentNumber();
    }

    @Override
    public void checkLogin() {
        if (getCurrentIdentity() != null) {
            return;
        }
        super.checkLogin();
    }

    public ExternalStudentIdentity getCurrentIdentity() {
        Object value = SaHolder.getStorage().get(STORAGE_IDENTITY_KEY);
        return value instanceof ExternalStudentIdentity identity ? identity : null;
    }
}
