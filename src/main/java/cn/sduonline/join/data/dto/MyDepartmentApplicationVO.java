package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.ApplicantApplicationStatus;
import cn.sduonline.join.data.po.DepartmentApplication;
import java.time.LocalDateTime;

/** 当前用户在指定部门的报名记录。 */
public record MyDepartmentApplicationVO(
        Long id,
        Long departmentId,
        ApplicantApplicationStatus status,
        LocalDateTime submittedAt
) {
    public static MyDepartmentApplicationVO from(
            DepartmentApplication application
    ) {
        return new MyDepartmentApplicationVO(
                application.getId(),
                application.getDepartmentId(),
                ApplicantApplicationStatus.from(application),
                application.getSubmittedAt()
        );
    }
}
