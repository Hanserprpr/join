package cn.sduonline.join.config;

import cn.sduonline.join.security.SaTokenAuthErrors;
import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sa-Token 拦截器：仅在首次 REQUEST 派发时执行注解鉴权，跳过异步重派发。
 * <p>
 * SSE（{@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}）
 * 等异步接口在连接建立、结束或超时时，容器会发起
 * {@link DispatcherType#ASYNC ASYNC} 重派发。Sa-Token 的上下文过滤器
 * {@code SaTokenContextFilterForJakartaServlet} 默认只注册在 REQUEST 派发上，
 * 异步线程中不会初始化 Sa-Token 上下文；若此时再次执行
 * {@code @SaCheckLogin} 等注解校验，会抛出
 * "SaTokenContext 上下文尚未初始化"。
 * <p>
 * 鉴权在首次 REQUEST 派发时已经通过，异步重派发不会再次执行 Controller 方法，
 * 因此跳过非 REQUEST 派发的重复校验是安全且必要的。
 * <p>
 * {@code @SaCheckLogin} / {@code @SaCheckRole} 在拦截器阶段抛出的未登录、
 * 无权限异常不会稳定进入 {@code @RestControllerAdvice}，这里直接写成统一 JSON，
 * 避免容器把它记成 {@code Servlet.service threw exception} 并返回 500。
 */
public class SaAsyncAwareInterceptor extends SaInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        try {
            return super.preHandle(request, response, handler);
        } catch (RuntimeException exception) {
            if (SaTokenAuthErrors.writeIfPresent(response, exception)) {
                return false;
            }
            throw exception;
        }
    }
}
