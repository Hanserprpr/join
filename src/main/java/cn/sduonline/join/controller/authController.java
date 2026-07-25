package cn.sduonline.join.controller;

import cn.sduonline.join.data.dto.ExternalStudentIdentity;
import cn.sduonline.join.data.dto.UserProfileVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.UserService;
import cn.sduonline.join.security.ExternalStpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证相关接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final ExternalStpLogic externalStpLogic;

    /**
     * 返回 OIDC 登录地址，由前端执行页面跳转
     */
    @GetMapping("/login")
    public Result<Map<String, String>> login() {
        return Result.ok(Map.of("loginUrl", "/oauth2/authorization/sdu"));
    }

    /**
     * 登录态检查
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(@AuthenticationPrincipal OidcUser oidcUser) {
        Map<String, Object> data = new LinkedHashMap<>();
        ExternalStudentIdentity externalIdentity = externalStpLogic.getCurrentIdentity();

        if (externalIdentity != null) {
            data.put("loggedIn", true);
            data.put("source", "external-token");
            data.put("casId", externalIdentity.studentNumber());
            data.put("name", externalIdentity.name());
        } else if (oidcUser != null) {
            data.put("loggedIn", true);
            data.put("source", "oidc");
            data.put("sub", oidcUser.getSubject());
            data.put("name", oidcUser.getFullName());
        } else {
            data.put("loggedIn", false);
        }
        return Result.ok(data);
    }

    /**
     * 返回当前登录用户的本地资料（OIDC 登录时已同步）
     */
    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        if (!StpUtil.isLogin()) {
            return Result.fail(BizCode.NOT_LOGIN, "未登录或登录已过期");
        }

        String casId = StpUtil.getLoginIdAsString();
        ExternalStudentIdentity externalIdentity = externalStpLogic.getCurrentIdentity();
        User user = externalIdentity != null
                ? userService.syncFromExternal(externalIdentity)
                : userService.findByCasId(casId).orElse(null);
        if (user == null) {
            return Result.fail(BizCode.NOT_LOGIN, "登录用户不存在");
        }

        return Result.ok(UserProfileVO.from(user));
    }

    /**
     * 返回原始 OIDC claims，供调试使用（需登录）。业务请优先调用 {@link #me}
     */
    @GetMapping("/oidc")
    @SaCheckLogin
    public Result<Map<String, Object>> oidcClaims(
            @AuthenticationPrincipal OidcUser oidcUser,
            HttpServletRequest request
    ) {
        if (oidcUser == null) {
            return Result.fail(BizCode.NOT_SUPPORTED, "当前登录方式没有 OIDC claims");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sub", oidcUser.getSubject());
        data.put("claims", oidcUser.getClaims());
        data.put("sessionId", request.getSession(false) != null
                ? request.getSession(false).getId()
                : null);
        return Result.ok(data);
    }
}
