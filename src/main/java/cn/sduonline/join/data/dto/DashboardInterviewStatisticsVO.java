package cn.sduonline.join.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "部门面试现场运营摘要")
public record DashboardInterviewStatisticsVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Integer publishedSessionCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Integer operationalSessionCount,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        DashboardHighlightedSessionVO highlightedSession,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long waitingCount
) {
}
