package team.kitemc.verifymc.user;

public record UserQuery(
        int page,
        int pageSize,
        String search,
        UserStatus status
) {
    public UserQuery {
        page = Math.max(1, page);
        pageSize = Math.max(1, pageSize);
        search = search == null ? "" : search.trim();
    }

    public int offset() {
        return (page - 1) * pageSize;
    }

    public boolean hasSearch() {
        return !search.isBlank();
    }
}
