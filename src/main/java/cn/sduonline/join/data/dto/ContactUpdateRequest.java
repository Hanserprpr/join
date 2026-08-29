package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户个人资料更新请求
 * 字段缺省或为 null 表示本次不修改
 * <p>
 * 选填字段（邮箱、QQ 号）额外支持传空字符串表示清空该字段。
 * 必填字段（手机号、学院、专业、入学年级）不支持清空，传空值等同于不修改。
 *
 * @param email 邮箱；传空字符串表示清空
 * @param phone 手机号
 * @param college 学院
 * @param major 专业
 * @param grade 入学年级
 * @param qq QQ 号（选填）；传空字符串表示清空
 */
public record ContactUpdateRequest(
        @Email
        @Pattern(regexp = "^(?:\\S+@\\S+\\.\\S+)?$")
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
