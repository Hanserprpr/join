package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.DepartmentAchievement;

/**
 * 部门成果展示信息
 *
 * @param title 成果标题
 * @param content 成果内容
 */
public record DepartmentAchievementVO(
        String title,
        String content
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
                achievement.getContent()
        );
    }
}
