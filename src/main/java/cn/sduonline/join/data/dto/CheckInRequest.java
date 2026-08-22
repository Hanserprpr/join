package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CheckInRequest(
        @Positive Long departmentId,
        @Size(max = 128) String token
) {
    public CheckInRequest(String token) {
        this(null, token);
    }
}
