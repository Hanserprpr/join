package cn.sduonline.join.data.dto;

import java.util.List;
import java.util.Map;

/**
 * 当前用户的权限画像
 *
 * @param casId 学号
 * @param name 用户姓名
 * @param roles 角色编码列表（含基础角色 {@code USER}），去重
 * @param permissions 权限编码列表，SYSTEM_ADMIN 为 {@code ["*"]}
 * @param isSystemAdmin 是否为平台管理员
 * @param scopedDepartments 权限编码 → 该用户可管理的启用部门 ID 列表
 * @param departmentAccess 该用户在各部门担任的身份及权限
 */
public record UserPermissionsVO(
        String casId,
        String name,
        List<String> roles,
        List<String> permissions,
        boolean isSystemAdmin,
        Map<String, List<Long>> scopedDepartments,
        List<DepartmentAccessVO> departmentAccess
) {
}
