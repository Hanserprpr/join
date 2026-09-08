package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 录取邮件正文支持 {name} 占位符，后端按收件学生姓名替换。 */
public record AdmissionPublishRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 10000) String content
) {
}
