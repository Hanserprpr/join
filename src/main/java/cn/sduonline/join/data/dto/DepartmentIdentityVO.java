package cn.sduonline.join.data.dto;

import java.util.List;

/**
 * 用户在某个部门中担任的身份
 *
 * @param roleCode 角色编码
 * @param roleName 角色名称
 * @param scopeType 身份来源作用域（ALL/BOARD/WORKSTATION/DEPARTMENT）
 * @param permissions 该身份在该部门拥有的权限编码，SYSTEM_ADMIN 为 {@code *}
 */
public record DepartmentIdentityVO(
        String roleCode,
        String roleName,
        String scopeType,
        List<String> permissions
) {
}
