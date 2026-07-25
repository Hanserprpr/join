package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardCreateRequest(
        @NotBlank
        @Size(max = 64)
        String name,

        @Min(0)
        Integer sortOrder,

        Boolean enabled
) {
}
