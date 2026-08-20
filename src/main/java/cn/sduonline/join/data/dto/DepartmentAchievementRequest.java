package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 部门成果写入参数
 * 展示顺序按请求数组顺序保存，无需额外传排序值
 *
 * @param title 成果标题
 * @param content 成果内容
 */
public record DepartmentAchievementRequest(
        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 5000)
        String content
) {
}
