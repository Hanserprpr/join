package cn.sduonline.join.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 部门成果写入参数
 * 展示顺序按请求数组顺序保存，无需额外传排序值
 *
 * @param title 成果标题
 * @param content 成果内容
 * @param imageUrls 成果图片地址列表
 */
public record DepartmentAchievementRequest(
        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 5000)
        String content,

        @Schema(
                description = "成果图片 URL 列表，按展示顺序排列",
                example = "[\"https://example.com/achievement-1.jpg\","
                        + "\"https://example.com/achievement-2.jpg\"]"
        )
        @Size(max = 10)
        List<@NotBlank @Size(max = 2048) String> imageUrls
) {
}
