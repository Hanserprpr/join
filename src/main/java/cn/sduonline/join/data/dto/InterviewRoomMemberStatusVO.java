package cn.sduonline.join.data.dto;

import java.time.LocalDateTime;

public record InterviewRoomMemberStatusVO(
        String casId,
        String name,
        boolean submitted,
        LocalDateTime submittedAt
) {
}
