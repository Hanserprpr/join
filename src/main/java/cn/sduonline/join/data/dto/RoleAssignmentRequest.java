package cn.sduonline.join.data.dto;

import cn.sduonline.join.security.scope.OrgType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RoleAssignmentRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,32}$")
        String casId,

        @NotBlank
        String roleCode,

        @NotNull
        OrgType scopeType,

        @NotNull
        Long scopeId
) {
}
