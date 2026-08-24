package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 面试场次创建或修改请求
 *
 * @param startsAt 面试开始时间
 * @param endsAt 面试结束时间
 * @param location 面试地点
 * @param checkInLimit 取号人数上限
 * @param qrCheckInEnabled 是否启用动态二维码签到
 * @param qrCodeTtlSeconds 签到二维码有效秒数
 */
public record InterviewSessionRequest(
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        @NotBlank @Size(max = 255) String location,
        @NotNull @Positive Integer checkInLimit,
        Boolean qrCheckInEnabled,
        @Min(5) @Max(86400) Integer qrCodeTtlSeconds
) {
}
