package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 部门海报写入参数
 *
 * @param url 海报地址
 * @param sortOrder 排序值
 */
public record DepartmentPosterRequest(
        @NotBlank
        @Size(max = 2048)
        String url,

        @Min(0)
        Integer sortOrder
) {
}
