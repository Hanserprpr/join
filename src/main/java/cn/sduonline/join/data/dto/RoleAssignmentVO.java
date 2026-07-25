package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.UserRoleScope;
import cn.sduonline.join.security.scope.OrgType;

public record RoleAssignmentVO(
        Long id,
        String casId,
        String roleCode,
        OrgType scopeType,
        Long scopeId
) {
    public static RoleAssignmentVO from(UserRoleScope assignment, String roleCode) {
        return new RoleAssignmentVO(
                assignment.getId(),
                assignment.getCasId(),
                roleCode,
                OrgType.valueOf(assignment.getScopeType()),
                assignment.getScopeId()
        );
    }
}
