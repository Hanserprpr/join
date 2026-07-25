package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.Workstation;

public record WorkstationVO(
        Long id,
        Long boardId,
        String name,
        Integer sortOrder,
        Boolean enabled
) {
    public static WorkstationVO from(Workstation workstation) {
        return new WorkstationVO(
                workstation.getId(),
                workstation.getBoardId(),
                workstation.getName(),
                workstation.getSortOrder(),
                workstation.getEnabled()
        );
    }
}
