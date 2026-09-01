package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.WeChatBindSessionCreatedVO;
import cn.sduonline.join.data.dto.WeChatBindSessionStatusVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.WeChatBindSessionService;
import cn.sduonline.join.service.WeChatBindingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/** 普通浏览器扫码绑定，以及复制到微信后打开的 OAuth 快捷流程。 */
@Slf4j
@RestController
@RequestMapping("/api/wechat/bind")
@RequiredArgsConstructor
public class WeChatBindController {

    private final WeChatBindSessionService sessionService;
    private final WeChatBindingService bindingService;

    /** 创建五分钟有效的一次性绑定链接，由前端将链接生成为二维码。 */
    @SaCheckLogin
    @PostMapping("/sessions")
    public Result<WeChatBindSessionCreatedVO> createSession(
            HttpServletRequest request
    ) {
        return Result.ok(sessionService.create(
                StpUtil.getLoginIdAsString(),
                request.getSession(true).getId()
        ));
    }

    /** 仅允许创建会话的登录用户在原浏览器中轮询。 */
    @SaCheckLogin
    @GetMapping("/sessions/{sessionId}")
    public Result<WeChatBindSessionStatusVO> sessionStatus(
            @PathVariable String sessionId,
            HttpServletRequest request
    ) {
        return Result.ok(sessionService.status(
                sessionId,
                StpUtil.getLoginIdAsString(),
                request.getSession(false) == null
                        ? "" : request.getSession(false).getId()
        ));
    }

    /** 复制到微信中打开：把短效绑定令牌换成更短的一次性 OAuth state。 */
    @GetMapping("/start")
    public RedirectView start(@RequestParam String token) {
        return new RedirectView(
                sessionService.createOAuthAuthorizationUrl(token));
    }

    /** 扫码绑定会话的 OAuth 回调。 */
    @GetMapping("/oauth/callback")
    public RedirectView oauthCallback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        boolean success = false;
        try {
            sessionService.completeOAuth(code, state);
            success = true;
        } catch (RuntimeException exception) {
            log.warn(
                    "WeChat QR binding OAuth callback failed: {}",
                    exception.getMessage());
        }
        return new RedirectView(bindingService.bindingResultUrl(success));
    }
}
