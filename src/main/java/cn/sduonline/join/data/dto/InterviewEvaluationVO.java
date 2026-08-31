package cn.sduonline.join.data.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InterviewEvaluationVO(
        Long interviewId,
        String administratorCasId,
        String administratorName,
        BigDecimal score,
        String evaluation,
        LocalDateTime submittedAt
) {
}
