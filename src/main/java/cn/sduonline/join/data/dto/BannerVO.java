package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.Banner;

/**
 * 轮播图展示信息
 *
 * @param id 轮播图 ID
 * @param url 轮播图图片地址
 * @param route 点击跳转路径，可空
 */
public record BannerVO(
        Long id,
        String url,
        String route
) {
    /**
     * 根据轮播图实体创建展示信息
     *
     * @param banner 轮播图实体
     * @return 轮播图展示信息
     */
    public static BannerVO from(Banner banner) {
        return new BannerVO(
                banner.getId(),
                banner.getUrl(),
                banner.getRoute()
        );
    }
}
