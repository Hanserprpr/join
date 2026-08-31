package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 单张部门海报的目标排序。 */
public record DepartmentPosterOrderItemRequest(
        @NotNull
        @Positive
        Long id,

        @NotNull
        @Min(0)
        Integer sortOrder
) {
}
