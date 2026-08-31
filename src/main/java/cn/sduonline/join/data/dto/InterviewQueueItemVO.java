package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.InterviewQueueStatus;
import java.time.LocalDateTime;

public record InterviewQueueItemVO(
        Long checkInId,
        Long applicationId,
        String candidateCasId,
        String candidateName,
        Integer queueNumber,
        Long queueOrder,
        Integer passCount,
        LocalDateTime checkedInAt,
        InterviewQueueStatus status,
        String interviewerCasId,
        String interviewerName,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Boolean priority,
        Long sessionId
) implements SessionScopedSnapshot {
}
