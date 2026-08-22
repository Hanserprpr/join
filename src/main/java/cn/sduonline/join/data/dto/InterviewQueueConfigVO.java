package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.InterviewPassMode;

public record InterviewQueueConfigVO(
        int passDelayCount,
        int maxPassCount,
        InterviewPassMode passMode
) {
    public InterviewQueueConfigVO(int passDelayCount, int maxPassCount) {
        this(passDelayCount, maxPassCount, InterviewPassMode.DELAY);
    }
}
