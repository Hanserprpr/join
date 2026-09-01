package cn.sduonline.join.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "部门报名数量")
public record DashboardDepartmentApplicationVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long departmentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String departmentName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long workstationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String workstationName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long applicationCount
) {
}
