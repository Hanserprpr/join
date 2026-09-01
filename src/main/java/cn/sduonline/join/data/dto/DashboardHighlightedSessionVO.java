package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.InterviewSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "总览中展示的已发布面试场次")
public record DashboardHighlightedSessionVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long sessionId,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String location,
        @Schema(nullable = true, format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime startsAt,
        @Schema(nullable = true, format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime endsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = "PUBLISHED")
        InterviewSessionStatus status
) {
}
