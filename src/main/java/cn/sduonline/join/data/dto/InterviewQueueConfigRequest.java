package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record InterviewQueueConfigRequest(
        @Min(1) @Max(100) int passDelayCount,
        @Min(0) @Max(20) int maxPassCount
) {
}
