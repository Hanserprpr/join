package cn.sduonline.join.controller;

import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.dto.WeChatProfileUrlVO;
import cn.sduonline.join.data.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供服务号主页链接，供外部浏览器跳转到微信内的服务号主页。 */
@RestController
@RequestMapping("/api/wechat")
@RequiredArgsConstructor
public class WeChatProfileController {

    private final WeChatProperties properties;

    @GetMapping("/profile-url")
    public Result<WeChatProfileUrlVO> profileUrl() {
        return Result.ok(new WeChatProfileUrlVO(
                properties.getOfficialAccountProfileUrl()));
    }
}
