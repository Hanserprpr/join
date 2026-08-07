package cn.sduonline.join.data.dto;

/**
 * 用户权限-可管理部门查询结果行
 *
 * @param permissionCode 权限编码，SYSTEM_ADMIN 用户映射为 {@code *}
 * @param departmentId 部门 ID
 */
public record PermissionDepartmentAccess(String permissionCode, Long departmentId) {
}
