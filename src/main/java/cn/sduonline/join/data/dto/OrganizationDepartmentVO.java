package cn.sduonline.join.data.dto;

/**
 * 用于组织管理和角色授权的组织树中的部门节点。
 *
 * @param id 部门 ID
 * @param name 部门名称
 */
public record OrganizationDepartmentVO(
        Long id,
        String name
) {
}
