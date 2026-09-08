package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 录取邮件正文支持 {name} 学生姓名和 {time} 北京日期（如 2026 年 9 月 8 日）占位符。 */
public record AdmissionPublishRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 10000) String content
) {
}
