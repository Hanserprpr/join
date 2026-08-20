package cn.sduonline.join.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.sduonline.join.data.enums.BizCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 将拦截器阶段的 Sa-Token 鉴权异常转成与 {@code Result} 相同的 HTTP 响应，
 * 避免异常冒泡到容器后被记成 500。
 */
public final class SaTokenAuthErrors {

    private SaTokenAuthErrors() {
    }

    /**
     * 未携带 Token 视为未登录；其余场景（过期、无效、被踢下线等）视为登录失效。
     */
    public static BizCode toBizCode(NotLoginException exception) {
        return NotLoginException.NOT_TOKEN.equals(exception.getType())
                ? BizCode.NOT_LOGIN
                : BizCode.TOKEN_INVALID;
    }

    public static void write(
            HttpServletResponse response,
            NotLoginException exception
    ) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, toBizCode(exception));
    }

    public static void writeNoPermission(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, BizCode.NO_PERMISSION);
    }

    public static boolean writeIfPresent(
            HttpServletResponse response,
            RuntimeException exception
    ) throws IOException {
        if (exception instanceof NotLoginException notLoginException) {
            write(response, notLoginException);
            return true;
        }
        if (exception instanceof NotPermissionException
                || exception instanceof NotRoleException) {
            writeNoPermission(response);
            return true;
        }
        return false;
    }

    private static void write(
            HttpServletResponse response,
            int httpStatus,
            BizCode bizCode
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        SecurityErrorResponseWriter.write(response, httpStatus, bizCode);
    }
}
