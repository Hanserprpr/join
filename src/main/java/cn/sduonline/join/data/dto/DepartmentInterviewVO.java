package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.DepartmentInterview;
import java.time.LocalDateTime;

public record DepartmentInterviewVO(
        Long id,
        Long departmentId,
        Long applicationId,
        String candidateCasId,
        String candidateName,
        String interviewerCasId,
        Integer queueNumber,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
    public static DepartmentInterviewVO from(DepartmentInterview source) {
        return new DepartmentInterviewVO(
                source.getId(), source.getDepartmentId(),
                source.getApplicationId(), source.getCandidateCasId(),
                source.getCandidateName(), source.getInterviewerCasId(),
                source.getQueueNumber(), source.getStartedAt(),
                source.getEndedAt()
        );
    }
}
