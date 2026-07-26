package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.ApplicationStatus;
import cn.sduonline.join.data.po.DepartmentApplication;
import java.time.LocalDateTime;

public record DepartmentApplicationSummaryVO(
        Long id,
        String casId,
        String applicantName,
        String college,
        String major,
        Integer grade,
        String phone,
        String qq,
        ApplicationStatus status,
        LocalDateTime submittedAt,
        Boolean interviewed,
        Long interviewId,
        Integer score
) {
    public static DepartmentApplicationSummaryVO from(
            DepartmentApplication application
    ) {
        return new DepartmentApplicationSummaryVO(
                application.getId(),
                application.getCasId(),
                application.getApplicantName(),
                application.getCollege(),
                application.getMajor(),
                application.getGrade(),
                application.getPhone(),
                application.getQq(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getInterviewed(),
                application.getInterviewId(),
                application.getScore()
        );
    }
}
