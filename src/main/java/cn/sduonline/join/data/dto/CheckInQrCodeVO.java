package cn.sduonline.join.data.dto;

import java.time.Instant;

public record CheckInQrCodeVO(
        String content,
        Instant expiresAt,
        long refreshAfterSeconds
) {
}
