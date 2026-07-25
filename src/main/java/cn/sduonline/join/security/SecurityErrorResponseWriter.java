package cn.sduonline.join.security;

import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.vo.Result;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/**
 * 在进入 Controller 之前输出与 {@code Result} 相同结构的认证错误。
 */
public final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    public static void write(
            HttpServletResponse response,
            int httpStatus,
            BizCode bizCode
    ) throws IOException {
        write(response, httpStatus, Result.fail(bizCode));
    }

    public static void write(
            HttpServletResponse response,
            int httpStatus,
            Result<?> result
    ) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"code":%d,"data":null,"msg":"%s","timestamp":%d}\
                """.formatted(
                result.getCode(),
                result.getMsg(),
                result.getTimestamp()
        ));
    }
}
