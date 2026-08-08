package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdmissionPublishRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 10000) String content
) {
}
