package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.Board;

public record BoardVO(
        Long id,
        String name,
        Integer sortOrder,
        Boolean enabled
) {
    public static BoardVO from(Board board) {
        return new BoardVO(
                board.getId(),
                board.getName(),
                board.getSortOrder(),
                board.getEnabled()
        );
    }
}
