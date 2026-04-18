package team.kitemc.verifymc.audit;

public record AuditQuery(AuditEventType eventType, String keyword, int page, int size) {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public AuditQuery {
        keyword = keyword == null ? "" : keyword.trim();
        page = page < 1 ? DEFAULT_PAGE : page;
        size = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }
}
