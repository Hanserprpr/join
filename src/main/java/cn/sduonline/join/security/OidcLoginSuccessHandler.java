package cn.sduonline.join.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.service.DepartmentCheckInService;
import cn.sduonline.join.service.ServiceResult;
import cn.sduonline.join.service.UserService;
import cn.sduonline.join.service.WeChatBindingService;
import cn.sduonline.join.service.WeChatLoginService;
import cn.sduonline.join.service.WeChatPendingLinkStore;
import cn.sduonline.join.service.WeChatPendingLinkStore.PendingLink;
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
 * 若本次登录是由未绑定的微信授权转过来的，先补绑该 OpenID，再续做原动作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final AppProperties appProperties;
    private final WeChatPendingLinkStore pendingLinkStore;
    private final WeChatBindingService bindingService;
    private final WeChatLoginService weChatLoginService;
    private final DepartmentCheckInService checkInService;

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

        PendingLink pendingLink = pendingLinkStore.take(request);
        if (pendingLink != null) {
            response.sendRedirect(completePendingWeChatLink(
                    localUser, pendingLink));
            return;
        }

        UriComponentsBuilder redirect = UriComponentsBuilder
                .fromUriString(appProperties.getFrontendUrl())
                .path("/")
                .queryParam("login", "success");

        response.sendRedirect(redirect.build().toUriString());
    }

    /**
     * 补绑微信并续做扫码时的动作。绑定失败不影响本次登录，
     * 只在回跳地址上标记结果。
     */
    private String completePendingWeChatLink(User user, PendingLink pendingLink) {
        boolean bound = true;
        try {
            bindingService.bindOpenId(user.getCasId(), pendingLink.openid());
        } catch (RuntimeException exception) {
            bound = false;
            log.warn(
                    "WeChat auto binding after OIDC login failed, casId={}, reason={}",
                    user.getCasId(),
                    exception.getMessage());
        }

        if (pendingLink.checkInGrant() != null) {
            ServiceResult<CheckInVO> result;
            try {
                result = checkInService.checkInCaptured(
                        pendingLink.checkInGrant(), user.getCasId());
            } catch (RuntimeException exception) {
                log.warn(
                        "Check-in after OIDC login failed, casId={}, reason={}",
                        user.getCasId(),
                        exception.getMessage());
                result = ServiceResult.failure(BizCode.SYSTEM_ERROR);
            }
            return weChatLoginService.checkInResultUrl(result);
        }

        return UriComponentsBuilder
                .fromUriString(appProperties.getFrontendUrl())
                .path("/")
                .queryParam("login", "success")
                .queryParam("source", "wechat")
                .queryParam("binding", bound ? "success" : "failed")
                .build()
                .toUriString();
    }
}
