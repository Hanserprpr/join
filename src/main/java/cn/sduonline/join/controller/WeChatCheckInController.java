package cn.sduonline.join.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.CheckInQrGrant;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.service.DepartmentCheckInService;
import cn.sduonline.join.service.ServiceResult;
import cn.sduonline.join.service.WeChatLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/** 微信扫码后的统一签到入口。 */
@RestController
@RequestMapping("/api/wechat/check-in")
@RequiredArgsConstructor
public class WeChatCheckInController {

    private final DepartmentCheckInService checkInService;
    private final WeChatLoginService loginService;

    @GetMapping("/entry")
    public RedirectView entry(@RequestParam String token) {
        ServiceResult<CheckInQrGrant> captured =
                checkInService.captureQrGrant(token);
        if (!captured.isSuccess()) {
            return new RedirectView(loginService.checkInResultUrl(
                    ServiceResult.failure(captured.error())));
        }

        if (!StpUtil.isLogin()) {
            return new RedirectView(loginService
                    .createCheckInAuthorizationUrl(captured.data()));
        }

        ServiceResult<CheckInVO> result = checkInService.checkInCaptured(
                captured.data(), StpUtil.getLoginIdAsString());
        return new RedirectView(loginService.checkInResultUrl(result));
    }
}
