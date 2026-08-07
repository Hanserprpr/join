package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.DepartmentInterviewRoom;
import java.time.LocalDateTime;
import java.util.List;

public record InterviewRoomVO(
        Long id,
        Long departmentId,
        Long sessionId,
        String name,
        String status,
        String createdBy,
        LocalDateTime createdAt,
        List<String> administratorCasIds
) {
    public static InterviewRoomVO from(
            DepartmentInterviewRoom room,
            List<String> members
    ) {
        return new InterviewRoomVO(
                room.getId(), room.getDepartmentId(), room.getSessionId(),
                room.getName(), room.getStatus(), room.getCreatedBy(),
                room.getCreatedAt(), members
        );
    }
}
