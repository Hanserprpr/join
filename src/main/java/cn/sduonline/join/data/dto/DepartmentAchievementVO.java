package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.DepartmentAchievement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 部门成果展示信息
 *
 * @param title 成果标题
 * @param content 成果内容
 * @param imageUrls 成果图片地址列表
 */
public record DepartmentAchievementVO(
        String title,
        String content,
        @Schema(description = "成果图片 URL 列表，按展示顺序排列")
        List<String> imageUrls
) {
    /**
     * 将部门成果实体转换为展示信息
     *
     * @param achievement 部门成果实体
     * @return 部门成果展示信息
     */
    public static DepartmentAchievementVO from(DepartmentAchievement achievement) {
        return new DepartmentAchievementVO(
                achievement.getTitle(),
                achievement.getContent(),
                achievement.getImageUrls() == null
                        ? List.of()
                        : List.copyOf(achievement.getImageUrls())
        );
    }
}
