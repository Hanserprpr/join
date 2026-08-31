package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.InterviewQueueStatus;
import java.util.List;

public record MyInterviewQueueStatusVO(
        Long departmentId,
        Long sessionId,
        Integer queueNumber,
        InterviewQueueStatus status,
        int peopleAhead,
        List<Integer> interviewingQueueNumbers
) implements SessionScopedSnapshot {
}
