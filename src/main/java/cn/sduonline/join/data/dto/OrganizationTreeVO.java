package cn.sduonline.join.data.dto;

import java.util.List;

/**
 * 用于组织管理和角色授权的组织树中的板块节点。
 *
 * @param id 板块 ID
 * @param name 板块名称
 * @param workstations 板块下的工作站
 */
public record OrganizationTreeVO(
        Long id,
        String name,
        List<OrganizationWorkstationVO> workstations
) {
}
