package team.kitemc.verifymc.db;

import java.util.List;

public record AuditPage(List<AuditRecord> items, int currentPage, int pageSize, long totalCount) {
    public AuditPage {
        items = items == null ? List.of() : List.copyOf(items);
        currentPage = Math.max(1, currentPage);
        pageSize = Math.max(1, pageSize);
        totalCount = Math.max(0, totalCount);
    }

    public int totalPages() {
        if (totalCount == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalCount / pageSize);
    }

    public boolean hasNext() {
        return currentPage < totalPages();
    }

    public boolean hasPrev() {
        return currentPage > 1 && totalPages() > 0;
    }
}
