package cn.sduonline.join.data.dto;

/**
 * 用户在某部门担任角色的权限查询结果行
 *
 * @param departmentId 部门 ID
 * @param departmentName 部门名称
 * @param roleCode 角色编码
 * @param roleName 角色名称
 * @param scopeType 角色作用域类型（ALL/BOARD/WORKSTATION/DEPARTMENT）
 * @param permissionCode 该角色在该部门授予的权限编码，SYSTEM_ADMIN 映射为 {@code *}
 */
public record DepartmentRoleAccess(
        Long departmentId,
        String departmentName,
        String roleCode,
        String roleName,
        String scopeType,
        String permissionCode
) {
}
