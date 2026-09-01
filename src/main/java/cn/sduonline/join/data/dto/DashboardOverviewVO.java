package cn.sduonline.join.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "管理员数据总览聚合统计")
public record DashboardOverviewVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        DashboardScopeVO scope,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        DashboardApplicationStatisticsVO applications,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED,
                description = "仅部门范围返回，板块和工作站范围为 null")
        DashboardInterviewStatisticsVO interviews,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
        OffsetDateTime generatedAt
) {
}
