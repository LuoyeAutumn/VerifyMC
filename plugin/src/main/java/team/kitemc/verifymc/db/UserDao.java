package team.kitemc.verifymc.db;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface UserDao {
    boolean registerUser(String username, String email, String status, String password);

    boolean registerUser(String username, String email, String status, String password,
                         Integer questionnaireScore, Boolean questionnairePassed,
                         String questionnaireReviewSummary, Long questionnaireScoredAt);

    boolean registerUserWithStoredPassword(String username, String email, String status, String storedPassword);

    boolean updateUserStatus(String username, String status);

    boolean updateUserPassword(String username, String plainPassword);

    boolean updateUserStoredPassword(String username, String storedPassword);

    boolean updateUserEmail(String username, String email);

    List<Map<String, Object>> getAllUsers();

    List<Map<String, Object>> getUsersWithPagination(int page, int pageSize);

    int getTotalUserCount();

    List<Map<String, Object>> getUsersWithPaginationAndSearch(int page, int pageSize, String searchQuery);

    int getTotalUserCountWithSearch(String searchQuery);

    int getApprovedUserCount();

    int getApprovedUserCountWithSearch(String searchQuery);

    List<Map<String, Object>> getApprovedUsersWithPagination(int page, int pageSize);

    List<Map<String, Object>> getApprovedUsersWithPaginationAndSearch(int page, int pageSize, String searchQuery);

    Map<String, Object> getUserByUsername(String username);

    Map<String, Object> getUserByUsernameExact(String username);

    Map<String, Object> getUserByEmail(String email);

    boolean deleteUser(String username);

    void save();

    int countUsersByEmail(String email);

    List<Map<String, Object>> getPendingUsers();

    boolean updateUserDiscordId(String username, String discordId);

    Map<String, Object> getUserByDiscordId(String discordId);

    boolean isDiscordIdLinked(String discordId);

    boolean isUsernameCaseSensitive();

    default Map<String, Object> getUserByUsernameConfigured(String username) {
        if (isUsernameCaseSensitive()) {
            return getUserByUsernameExact(username);
        }
        return getUserByUsername(username);
    }

    default Map<String, Object> getUserByUsernameIgnoreCase(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return getAllUsers().stream()
            .filter(u -> username.equalsIgnoreCase((String) u.get("username")))
            .findFirst()
            .orElse(null);
    }

    default List<Map<String, Object>> findAllByEmail(String email) {
        if (email == null || email.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return getAllUsers().stream()
            .filter(u -> email.equalsIgnoreCase((String) u.get("email")))
            .collect(java.util.stream.Collectors.toList());
    }

    default List<List<String>> findUsernameCaseConflictGroups() {
        return getAllUsers().stream()
            .map(u -> (String) u.get("username"))
            .filter(username -> username != null && !username.isBlank())
            .collect(java.util.stream.Collectors.groupingBy(
                    username -> username.toLowerCase(Locale.ROOT),
                    java.util.stream.Collectors.toList()
            ))
            .values().stream()
            .filter(group -> group.size() > 1)
            .map(group -> group.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList())
            .sorted(java.util.Comparator.comparing(group -> group.get(0).toLowerCase(Locale.ROOT)))
            .toList();
    }

    default boolean updateUserStatus(String username, String status, String operator) {
        return updateUserStatus(username, status);
    }

    default List<Map<String, Object>> getUsers(int page, int size, String search, String status) {
        if (status == null || status.isEmpty() || "all".equalsIgnoreCase(status)) {
            return getUsersWithPaginationAndSearch(page, size, search);
        }
        if ("approved".equalsIgnoreCase(status)) {
            return getApprovedUsersWithPaginationAndSearch(page, size, search);
        }
        return getUsersByStatus(status, page, size, search);
    }

    default int getTotalUsers(String search, String status) {
        if (status == null || status.isEmpty() || "all".equalsIgnoreCase(status)) {
            return getTotalUserCountWithSearch(search);
        }
        if ("approved".equalsIgnoreCase(status)) {
            return getApprovedUserCountWithSearch(search);
        }
        return getTotalUsersByStatus(status, search);
    }

    default boolean banUser(String username) {
        return updateUserStatus(username, "banned");
    }

    default boolean unbanUser(String username) {
        return updateUserStatus(username, "approved");
    }

    default List<Map<String, Object>> getUsersByStatus(String status) {
        List<Map<String, Object>> allUsers = getAllUsers();
        return allUsers.stream()
            .filter(u -> status.equalsIgnoreCase((String) u.get("status")))
            .collect(java.util.stream.Collectors.toList());
    }

    default List<Map<String, Object>> getUsersByStatus(String status, int page, int size, String search) {
        if (page < 1 || size <= 0) {
            return java.util.Collections.emptyList();
        }

        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();
        List<Map<String, Object>> filtered = getAllUsers().stream()
            .filter(u -> status.equalsIgnoreCase(String.valueOf(u.get("status"))))
            .filter(u -> matchesSearch(u, normalizedSearch))
            .sorted((a, b) -> Long.compare(getRegTimeAsLong(b.get("regTime")), getRegTimeAsLong(a.get("regTime"))))
            .collect(java.util.stream.Collectors.toList());

        int from = (page - 1) * size;
        if (from >= filtered.size()) {
            return java.util.Collections.emptyList();
        }
        int to = Math.min(from + size, filtered.size());
        return filtered.subList(from, to);
    }

    default int getTotalUsersByStatus(String status, String search) {
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();
        return (int) getAllUsers().stream()
            .filter(u -> status.equalsIgnoreCase(String.valueOf(u.get("status"))))
            .filter(u -> matchesSearch(u, normalizedSearch))
            .count();
    }

    default boolean matchesSearch(Map<String, Object> user, String normalizedSearch) {
        if (normalizedSearch == null || normalizedSearch.isEmpty()) {
            return true;
        }
        String username = String.valueOf(user.getOrDefault("username", "")).toLowerCase();
        String email = String.valueOf(user.getOrDefault("email", "")).toLowerCase();
        return username.contains(normalizedSearch) || email.contains(normalizedSearch);
    }

    default long getRegTimeAsLong(Object regTime) {
        if (regTime instanceof Number) {
            return ((Number) regTime).longValue();
        }
        if (regTime == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(regTime));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    default boolean updatePassword(String username, String plainPassword) {
        return updateUserPassword(username, plainPassword);
    }

    default void close() {
    }
}
