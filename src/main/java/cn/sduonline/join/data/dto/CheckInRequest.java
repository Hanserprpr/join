package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CheckInRequest(
        @Positive Long departmentId,
        @Positive Long sessionId,
        @Size(max = 128) String token
) {
    public CheckInRequest(String token) {
        this(null, null, token);
    }
}
