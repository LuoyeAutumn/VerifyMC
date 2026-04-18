package team.kitemc.verifymc.user;

import java.util.List;

public record UserPage<T>(
        List<T> items,
        int currentPage,
        int pageSize,
        long totalCount,
        int totalPages,
        boolean hasNext,
        boolean hasPrev
) {
    public static <T> UserPage<T> of(List<T> items, int currentPage, int pageSize, long totalCount) {
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
        return new UserPage<>(
                items,
                currentPage,
                pageSize,
                totalCount,
                totalPages,
                currentPage < totalPages,
                currentPage > 1 && totalPages > 0
        );
    }
}
