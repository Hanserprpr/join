package cn.sduonline.join.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.DepartmentCheckInService;
import cn.sduonline.join.service.ServiceResult;
import cn.sduonline.join.service.WeChatLoginService;
import cn.sduonline.join.service.WeChatLoginService.AuthenticationResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/** 手机微信内的已绑定账号登录。 */
@Slf4j
@RestController
@RequestMapping("/api/wechat/login")
@RequiredArgsConstructor
public class WeChatLoginController {

    private final WeChatLoginService loginService;
    private final DepartmentCheckInService checkInService;

    /** 生成匿名可访问的微信网页授权地址。 */
    @GetMapping("/url")
    public Result<Map<String, String>> authorizationUrl() {
        return Result.ok(Map.of(
                "authorizationUrl", loginService.createAuthorizationUrl()));
    }

    /** 微信授权成功后，在当前微信浏览器建立登录态。 */
    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam String code,
            @RequestParam String state) {
        boolean success = false;
        try {
            AuthenticationResult authentication =
                    loginService.authenticate(code, state);
            User user = authentication.user();
            StpUtil.login(user.getCasId());
            if (authentication.checkInGrant() != null) {
                ServiceResult<CheckInVO> result =
                        checkInService.checkInCaptured(
                                authentication.checkInGrant(),
                                user.getCasId());
                return new RedirectView(loginService.checkInResultUrl(result));
            }
            success = true;
        } catch (RuntimeException exception) {
            log.warn("WeChat login callback failed", exception);
        }
        return new RedirectView(loginService.loginResultUrl(success));
    }
}
