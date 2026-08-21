package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.ApplicationStatus;
import cn.sduonline.join.data.po.DepartmentApplication;
import java.time.LocalDateTime;

/**
 * 当前用户的单条报名记录，附带所属部门、工作站与板块信息。
 */
public record MyApplicationVO(
        Long id,
        Long departmentId,
        String departmentName,
        Long workstationId,
        String workstationName,
        Long boardId,
        String boardName,
        ApplicationStatus status,
        LocalDateTime submittedAt
) {
    public static MyApplicationVO from(DepartmentApplication application) {
        return new MyApplicationVO(
                application.getId(),
                application.getDepartmentId(),
                application.getDepartmentName(),
                application.getWorkstationId(),
                application.getWorkstationName(),
                application.getBoardId(),
                application.getBoardName(),
                application.getStatus(),
                application.getSubmittedAt()
        );
    }
}
