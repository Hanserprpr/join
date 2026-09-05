package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.enums.ApplicationStatus;
import java.time.LocalDateTime;
import java.util.List;

public record DepartmentApplicationDetailVO(
        Long id,
        Long departmentId,
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
        Campus campus
) {
}
