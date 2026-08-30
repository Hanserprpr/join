package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 组织节点名称修改请求。 */
public record OrganizationNameUpdateRequest(
        @NotBlank
        @Size(max = 64)
        String name
) {
}
