package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.Workstation;

/**
 * 工作站摘要信息
 *
 * @param id 工作站 ID
 * @param name 工作站名称
 */
public record WorkstationSummaryVO(
        Long id,
        String name
) {
    /**
     * 将工作站实体转换为摘要信息
     *
     * @param workstation 工作站实体
     * @return 工作站摘要信息
     */
    public static WorkstationSummaryVO from(Workstation workstation) {
        return new WorkstationSummaryVO(
                workstation.getId(),
                workstation.getName()
        );
    }
}
