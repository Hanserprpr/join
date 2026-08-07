package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.DepartmentInterview;
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
        LocalDateTime endedAt
) {
    public static DepartmentInterviewVO from(DepartmentInterview source) {
        return new DepartmentInterviewVO(
                source.getId(), source.getDepartmentId(), source.getRoomId(),
                source.getApplicationId(), source.getCandidateCasId(),
                source.getCandidateName(), source.getInterviewerCasId(),
                source.getInterviewerName(),
                source.getQueueNumber(), source.getStartedAt(),
                source.getEndedAt()
        );
    }
}
