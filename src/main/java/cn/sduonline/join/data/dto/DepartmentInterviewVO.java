package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.DepartmentInterview;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DepartmentInterviewVO(
        Long id,
        Long departmentId,
        Long roomId,
        Long applicationId,
        String candidateCasId,
        String candidateName,
        String interviewerCasId,
        String interviewerName,
        Integer queueNumber,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        BigDecimal score,
        String evaluation
) {
    public static DepartmentInterviewVO from(DepartmentInterview source) {
        return from(source, null, null);
    }

    public static DepartmentInterviewVO from(
            DepartmentInterview source, BigDecimal score, String evaluation
    ) {
        return new DepartmentInterviewVO(
                source.getId(), source.getDepartmentId(), source.getRoomId(),
                source.getApplicationId(), source.getCandidateCasId(),
                source.getCandidateName(), source.getInterviewerCasId(),
                source.getInterviewerName(),
                source.getQueueNumber(), source.getStartedAt(),
                source.getEndedAt(),
                score, evaluation
        );
    }
}
