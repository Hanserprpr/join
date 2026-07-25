package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record InterviewQueueConfigPatchRequest(
        @Min(1) @Max(100) Integer passDelayCount,
        @Min(0) @Max(20) Integer maxPassCount
) {
}
