package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.Campus;

/**
 * 公开组织树中的部门展示信息。
 *
 * @param id 部门 ID
 * @param name 部门名称
 * @param campus 所在校区
 * @param assetId 部门公开素材 ID
 * @param introduction 部门公开简介
 */
public record OrganizationDepartmentVO(
        Long id,
        String name,
        Campus campus,
        Long assetId,
        String introduction
) {
}
