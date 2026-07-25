package cn.sduonline.join.security;

import cn.sduonline.join.data.enums.BizCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * API 未认证时返回 JSON 401，而不是重定向到 OIDC 登录页。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            HttpServletResponse response,
            @NonNull AuthenticationException authException
    ) throws IOException {
        SecurityErrorResponseWriter.write(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                BizCode.NOT_LOGIN
        );
    }
}
