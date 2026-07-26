package cn.sduonline.join.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OIDC 登录成功处理器：按 {@code sub} 同步本地用户，并重定向到前端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final AppProperties appProperties;

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        User localUser = null;
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            localUser = userService.syncFromOidc(oidcUser);
            log.info("OIDC login success, sub={}", oidcUser.getSubject());
        } else {
            log.warn("OIDC login success but principal is not OidcUser: {}",
                    Objects.requireNonNull(authentication.getPrincipal()).getClass().getName());
        }

        if (localUser == null) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "OIDC 用户信息无效"
            );
            return;
        }
        StpUtil.login(localUser.getCasId());

        UriComponentsBuilder redirect = UriComponentsBuilder
                .fromUriString(appProperties.getFrontendUrl())
                .path("/")
                .queryParam("login", "success");

        response.sendRedirect(redirect.build().toUriString());
    }
}
