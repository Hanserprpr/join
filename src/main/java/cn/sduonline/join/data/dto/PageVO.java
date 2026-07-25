package cn.sduonline.join.data.dto;

import java.util.List;

public record PageVO<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages
) {
    public static <T> PageVO<T> of(
            List<T> items, long total, int page, int size
    ) {
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageVO<>(items, total, page, size, totalPages);
    }
}
