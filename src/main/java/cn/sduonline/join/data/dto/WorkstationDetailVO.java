package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.Workstation;
import java.util.List;

/**
 * 工作站详情
 *
 * @param id 工作站 ID
 * @param boardId 所属板块 ID
 * @param name 工作站名称
 * @param departments 工作站下的部门
 */
public record WorkstationDetailVO(
        Long id,
        Long boardId,
        String name,
        List<DepartmentSummaryVO> departments
) {
    /**
     * 根据工作站实体和部门列表创建工作站详情
     *
     * @param workstation 工作站实体
     * @param departments 部门列表
     * @return 工作站详情
     */
    public static WorkstationDetailVO from(
            Workstation workstation,
            List<DepartmentSummaryVO> departments
    ) {
        return new WorkstationDetailVO(
                workstation.getId(),
                workstation.getBoardId(),
                workstation.getName(),
                departments
        );
    }
}
