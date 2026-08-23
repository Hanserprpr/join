package cn.sduonline.join.data.dto;

import java.util.List;

/**
 * 用于组织管理和角色授权的组织树中的工作站节点。
 *
 * @param id 工作站 ID
 * @param name 工作站名称
 * @param departments 工作站下的部门
 */
public record OrganizationWorkstationVO(
        Long id,
        String name,
        List<OrganizationDepartmentVO> departments
) {
}
