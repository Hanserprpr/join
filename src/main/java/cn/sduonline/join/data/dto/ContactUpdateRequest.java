package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户个人资料更新请求
 * 字段为空表示本次不修改
 *
 * @param email 邮箱
 * @param phone 手机号
 * @param college 学院
 * @param major 专业
 * @param grade 入学年级
 * @param qq QQ 号（选填）
 */
public record ContactUpdateRequest(
        @Email
        @Pattern(regexp = "^\\S+@\\S+\\.\\S+$")
        String email,

        @Pattern(regexp = "^1\\d{10}$")
        String phone,

        @Size(max = 64)
        String college,

        @Size(max = 64)
        String major,

        @Min(2000)
        @Max(2100)
        Integer grade,

        @Pattern(regexp = "^(?:[1-9]\\d{4,11})?$")
        String qq
) {
}
