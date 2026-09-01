package cn.sduonline.join.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "全量报名的学院分布")
public record DashboardCollegeDistributionVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String college,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long applicationCount
) {
}
