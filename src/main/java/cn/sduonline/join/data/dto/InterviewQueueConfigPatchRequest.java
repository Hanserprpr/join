package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.InterviewPassMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record InterviewQueueConfigPatchRequest(
        @Min(1) @Max(100) Integer passDelayCount,
        @Min(0) @Max(20) Integer maxPassCount,
        InterviewPassMode passMode
) {
    public InterviewQueueConfigPatchRequest(
            Integer passDelayCount, Integer maxPassCount
    ) {
        this(passDelayCount, maxPassCount, null);
    }
}
