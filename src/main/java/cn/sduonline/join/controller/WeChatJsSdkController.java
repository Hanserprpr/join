package cn.sduonline.join.controller;

import cn.sduonline.join.data.dto.WeChatJsSdkConfigVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.WeChatJsSdkService;
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
     */
    @GetMapping("/config")
    public Result<WeChatJsSdkConfigVO> config(@RequestParam String url) {
        return Result.ok(jsSdkService.createConfig(url));
    }
}
