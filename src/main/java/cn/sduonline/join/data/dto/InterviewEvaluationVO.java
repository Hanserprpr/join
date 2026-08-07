package cn.sduonline.join.data.dto;

import java.time.LocalDateTime;

public record InterviewEvaluationVO(
        Long interviewId,
        String administratorCasId,
        String administratorName,
        Integer score,
        String evaluation,
        LocalDateTime submittedAt
) {
}
