package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.enums.Campus;
import java.util.List;

/**
 * 部门详情展示信息
 *
 * @param id 部门 ID
 * @param workstationId 所属工作站 ID
 * @param name 部门名称
 * @param campus 所在校区
 * @param introduction 组织介绍
 * @param posters 部门海报
 * @param achievements 部门成果
 * @param recruitmentRequirements 纳新要求
 * @param contact 联系方式
 * @param recruitmentGroup 纳新群信息
 * @param hasQuestionnaire 是否设置了报名问卷
 * @param sortOrder 排序值
 * @param enabled 是否启用
 */
public record DepartmentVO(
        Long id,
        Long workstationId,
        String name,
        Campus campus,
        String introduction,
        List<DepartmentPosterVO> posters,
        String achievements,
        String recruitmentRequirements,
        String contact,
        String recruitmentGroup,
        boolean hasQuestionnaire,
        Integer sortOrder,
        Boolean enabled
) {
    /**
     * 根据部门实体和海报列表创建部门详情
     *
     * @param department 部门实体
     * @param posters 海报列表
     * @return 部门详情
     */
    public static DepartmentVO from(
            Department department,
            List<DepartmentPosterVO> posters,
            boolean hasQuestionnaire
    ) {
        return new DepartmentVO(
                department.getId(),
                department.getWorkstationId(),
                department.getName(),
                department.getCampus(),
                department.getIntroduction(),
                posters,
                department.getAchievements(),
                department.getRecruitmentRequirements(),
                department.getContact(),
                department.getRecruitmentGroup(),
                hasQuestionnaire,
                department.getSortOrder(),
                department.getEnabled()
        );
    }

    /**
     * 根据部门实体创建不含海报的部门详情
     *
     * @param department 部门实体
     * @return 不含海报的部门详情
     */
    public static DepartmentVO from(Department department) {
        return from(department, List.of(), false);
    }
}
