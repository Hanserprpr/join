package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.WeChatBindingStatusVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.WeChatBindingService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@Slf4j
@RestController
@RequestMapping("/api/wechat/binding")
@RequiredArgsConstructor
public class WeChatBindingController {

    private final WeChatBindingService bindingService;

    /**
     * 生成当前用户的微信授权绑定地址
     *
     * @return 微信授权地址
     */
    @SaCheckLogin
    @GetMapping("/url")
    public Result<Map<String, String>> authorizationUrl() {
        String url = bindingService.createAuthorizationUrl(
                StpUtil.getLoginIdAsString());
        return Result.ok(Map.of("authorizationUrl", url));
    }

    /**
     * 处理微信 OAuth 授权回调并跳转至绑定结果页
     *
     * @param code 微信授权码
     * @param state 绑定状态令牌
     * @return 绑定结果页重定向
     */
    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam String code,
            @RequestParam String state) {
        boolean success = false;
        try {
            bindingService.completeBinding(code, state);
            success = true;
        } catch (RuntimeException exception) {
            log.warn("WeChat binding callback failed: {}", exception.getMessage());
        }
        return new RedirectView(bindingService.bindingResultUrl(success));
    }

    /**
     * 查询当前用户的微信绑定状态
     *
     * @return 微信绑定状态
     */
    @SaCheckLogin
    @GetMapping("/status")
    public Result<WeChatBindingStatusVO> status() {
        return Result.ok(new WeChatBindingStatusVO(
                bindingService.isBound(StpUtil.getLoginIdAsString())));
    }

    /**
     * 解除当前用户的微信绑定
     *
     * @return 空结果
     */
    @SaCheckLogin
    @DeleteMapping
    public Result<Void> unbind() {
        bindingService.unbind(StpUtil.getLoginIdAsString());
        return Result.ok();
    }
}
