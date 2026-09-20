package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.ApplicationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DepartmentApplicationExportVO(
        Long id,
        String casId,
        String applicantName,
        String college,
        String major,
        Integer grade,
        String phone,
        String email,
        String qq,
        ApplicationStatus status,
        LocalDateTime submittedAt,
        List<ApplicationAnswerVO> answers,
        Boolean interviewed,
        List<InterviewEvaluationVO> evaluations,
        BigDecimal averageScore
) {
}
