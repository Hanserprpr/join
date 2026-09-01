package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.po.Department;
import java.util.List;

/**
 * 工作站详情中的部门摘要信息
 *
 * @param id 部门 ID
 * @param name 部门名称
 * @param campuses 所在校区（可多个）
 * @param assetId 部门素材 ID
 */
public record DepartmentSummaryVO(
        Long id,
        String name,
        List<Campus> campuses,
        Long assetId
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
                department.getCampuses() == null ? List.of() : department.getCampuses(),
                department.getAssetId()
        );
    }
}
