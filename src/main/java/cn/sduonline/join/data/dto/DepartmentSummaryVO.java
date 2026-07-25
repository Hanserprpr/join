package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.po.Department;

/**
 * 工作站详情中的部门摘要信息
 *
 * @param id 部门 ID
 * @param name 部门名称
 * @param campus 所在校区
 */
public record DepartmentSummaryVO(
        Long id,
        String name,
        Campus campus
) {
    /**
     * 将部门实体转换为摘要信息
     *
     * @param department 部门实体
     * @return 部门摘要信息
     */
    public static DepartmentSummaryVO from(Department department) {
        return new DepartmentSummaryVO(
                department.getId(),
                department.getName(),
                department.getCampus()
        );
    }
}
