package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.Board;
import java.util.List;

/**
 * 包含工作站列表的板块展示信息
 *
 * @param id 板块 ID
 * @param name 板块名称
 * @param sortOrder 排序值
 * @param workstations 板块下的工作站
 */
public record BoardWithWorkstationsVO(
        Long id,
        String name,
        Integer sortOrder,
        List<WorkstationSummaryVO> workstations
) {
    /**
     * 根据板块实体和工作站列表创建展示信息
     *
     * @param board 板块实体
     * @param workstations 工作站列表
     * @return 板块展示信息
     */
    public static BoardWithWorkstationsVO from(
            Board board,
            List<WorkstationSummaryVO> workstations
    ) {
        return new BoardWithWorkstationsVO(
                board.getId(),
                board.getName(),
                board.getSortOrder(),
                workstations
        );
    }
}
