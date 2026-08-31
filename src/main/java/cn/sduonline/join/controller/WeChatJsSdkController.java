package cn.sduonline.join.controller;

import cn.sduonline.join.data.dto.WeChatJsSdkConfigVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.WeChatJsSdkService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wechat/js-sdk")
@RequiredArgsConstructor
public class WeChatJsSdkController {

    private final WeChatJsSdkService jsSdkService;

    /**
     * 为当前前端页面生成微信 JS-SDK 签名。
     *
     * @deprecated 订阅通知已废弃，叫号通知已改用模板消息。
     */
    @Deprecated(forRemoval = true)
    @Operation(
            summary = "获取微信 JS-SDK 签名和订阅通知模板",
            description = "已废弃：叫号通知已改用普通模板消息。",
            deprecated = true
    )
    @GetMapping("/config")
    public Result<WeChatJsSdkConfigVO> config(@RequestParam String url) {
        return Result.ok(jsSdkService.createConfig(url));
    }
}
