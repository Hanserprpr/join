package cn.sduonline.join.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "报名聚合统计")
public record DashboardApplicationStatisticsVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long total,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Integer departmentsWithApplications,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Double averagePerDepartment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<DashboardDepartmentApplicationVO> byDepartment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<DashboardCollegeDistributionVO> byCollege
) {
}
