package cn.sduonline.join.data.dto;

import cn.sduonline.join.security.scope.OrgType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "统计所属的组织范围")
public record DashboardScopeVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OrgType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Integer departmentCount
) {
}
