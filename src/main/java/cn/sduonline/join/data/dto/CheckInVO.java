package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.DepartmentCheckIn;
import java.time.LocalDateTime;

public record CheckInVO(
        Long id,
        Long departmentId,
        Long sessionId,
        Long applicationId,
        LocalDateTime checkedInAt,
        Integer queueNumber,
        Boolean priority
) {
    public static CheckInVO from(DepartmentCheckIn source) {
        return new CheckInVO(
                source.getId(), source.getDepartmentId(),
                source.getSessionId(), source.getApplicationId(),
                source.getCheckedInAt(), source.getQueueNumber(),
                source.getPriority()
        );
    }
}
