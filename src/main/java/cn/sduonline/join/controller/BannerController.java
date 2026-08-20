package cn.sduonline.join.controller;

import cn.sduonline.join.data.dto.BannerVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.BannerService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页轮播图接口
 */
@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    /**
     * 轮播图列表0
     */
    @GetMapping
    public Result<List<BannerVO>> getBanners() {
        return Result.ok(bannerService.findAll());
    }
}
