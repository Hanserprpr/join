package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.DepartmentPoster;

/**
 * 部门海报展示信息
 *
 * @param id 海报 ID
 * @param url 海报地址
 * @param sortOrder 排序值
 */
public record DepartmentPosterVO(
        Long id,
        String url,
        Integer sortOrder
) {
    /**
     * 将部门海报实体转换为展示信息
     *
     * @param poster 部门海报实体
     * @return 部门海报展示信息
     */
    public static DepartmentPosterVO from(DepartmentPoster poster) {
        return from(poster, poster.getUrl());
    }

    /** 使用指定的对外访问 URL 转换展示信息。 */
    public static DepartmentPosterVO from(DepartmentPoster poster, String accessUrl) {
        return new DepartmentPosterVO(
                poster.getId(),
                accessUrl,
                poster.getSortOrder()
        );
    }
}
