package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.ApplicationStatus;
import cn.sduonline.join.data.po.DepartmentApplication;
import java.time.LocalDateTime;

public record DepartmentApplicationVO(
        Long id,
        Long departmentId,
        ApplicationStatus status,
        LocalDateTime submittedAt
) {
    public static DepartmentApplicationVO from(DepartmentApplication application) {
        return new DepartmentApplicationVO(
                application.getId(),
                application.getDepartmentId(),
                application.getStatus(),
                application.getSubmittedAt()
        );
    }
}
