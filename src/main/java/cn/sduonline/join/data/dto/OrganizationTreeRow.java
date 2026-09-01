package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.Campus;
import java.util.List;

/**
 * 组织树查询的扁平行，由服务层按板块、工作站、部门聚合。
 *
 * @param boardId 板块 ID
 * @param boardName 板块名称
 * @param workstationId 工作站 ID；板块没有工作站时为空
 * @param workstationName 工作站名称；板块没有工作站时为空
 * @param departmentId 部门 ID；工作站没有部门时为空
 * @param departmentName 部门名称；工作站没有部门时为空
 * @param departmentCampuses 部门所在校区列表
 * @param departmentAssetId 部门公开素材 ID
 * @param departmentIntroduction 部门公开简介
 */
public record OrganizationTreeRow(
        Long boardId,
        String boardName,
        Long workstationId,
        String workstationName,
        Long departmentId,
        String departmentName,
        List<Campus> departmentCampuses,
        Long departmentAssetId,
        String departmentIntroduction
) {
}
