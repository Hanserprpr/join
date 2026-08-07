package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InterviewRoomRequest(
        @Positive Long sessionId,
        @NotBlank @Size(max = 64) String name
) {
}
