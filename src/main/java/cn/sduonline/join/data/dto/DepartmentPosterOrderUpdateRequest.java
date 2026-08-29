package cn.sduonline.join.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 部门全部海报的目标排序。 */
public record DepartmentPosterOrderUpdateRequest(
        @NotNull
        @Size(max = 20)
        List<@NotNull @Valid DepartmentPosterOrderItemRequest> posters
) {
}
