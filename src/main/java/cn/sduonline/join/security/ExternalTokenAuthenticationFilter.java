package cn.sduonline.join.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.sduonline.join.data.dto.ExternalStudentIdentity;
import cn.sduonline.join.data.enums.BizCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 优先识别 {@code token} Header，并兼容 Bearer Token；
 * 没有外部 Token 时保留原有 OIDC Session 登录流程。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalTokenAuthenticationFilter extends OncePerRequestFilter {

    private final ExternalStpLogic externalStpLogic;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            ExternalStudentIdentity identity = externalStpLogic.authenticate(token);
            externalStpLogic.checkLogin();

            var authentication = new UsernamePasswordAuthenticationToken(
                    identity,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_EXTERNAL_STUDENT"))
            );
            authentication.setDetails(identity);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (NotLoginException ex) {
            SecurityContextHolder.clearContext();
            log.warn(
                    "External token authentication failed, uri={}, reason={}",
                    request.getRequestURI(),
                    ex.getMessage()
            );
            SecurityErrorResponseWriter.write(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    BizCode.TOKEN_INVALID
            );
        } catch (ExternalIdentityUnavailableException ex) {
            SecurityContextHolder.clearContext();
            log.error(
                    "External identity service unavailable, uri={}",
                    request.getRequestURI(),
                    ex
            );
            SecurityErrorResponseWriter.write(
                    response,
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    BizCode.THIRD_PARTY_UNAVAILABLE
            );
        }
    }

    private static String extractToken(HttpServletRequest request) {
        String tokenHeader = request.getHeader("token");
        if (StringUtils.hasText(tokenHeader)) {
            return tokenHeader.trim();
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return StringUtils.hasText(token) ? token : null;
    }

}
