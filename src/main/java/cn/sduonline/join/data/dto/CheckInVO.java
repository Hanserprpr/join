package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.DepartmentCheckIn;
import java.time.LocalDateTime;

public record CheckInVO(
        Long id,
        Long departmentId,
        Long applicationId,
        LocalDateTime checkedInAt,
        Integer queueNumber
) {
    public static CheckInVO from(DepartmentCheckIn source) {
        return new CheckInVO(
                source.getId(), source.getDepartmentId(),
                source.getApplicationId(), source.getCheckedInAt(),
                source.getQueueNumber()
        );
    }
}
