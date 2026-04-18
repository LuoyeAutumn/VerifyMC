package team.kitemc.verifymc.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends AutoCloseable {
    boolean isUsernameCaseSensitive();

    boolean create(NewUserRecord user);

    Optional<UserRecord> findByUsernameConfigured(String username);

    Optional<UserRecord> findByUsernameIgnoreCase(String username);

    Optional<UserRecord> findByUsernameExact(String username);

    default Optional<UserRecord> findByUsername(String username) {
        return findByUsernameConfigured(username);
    }

    Optional<UserRecord> findByEmail(String email);

    List<UserRecord> findAllByEmail(String email);

    Optional<UserRecord> findByDiscordId(String discordId);

    List<UserRecord> findAll();

    List<UserSummary> findUsersByStatus(UserStatus status);

    UserPage<UserSummary> findUsers(UserQuery query);

    List<String> suggestUsernames(String prefix, int limit);

    long countByEmail(String email);

    boolean updateStatus(String username, UserStatus status, String operator);

    boolean updateStatusForBan(String username, String operator);

    boolean restoreStatusFromBan(String username, UserStatus fallbackStatus, String operator);

    boolean updatePassword(String username, String plainPassword);

    boolean updateStoredPassword(String username, String storedPassword);

    boolean updateEmail(String username, String email);

    boolean updateDiscordId(String username, String discordId);

    boolean delete(String username);

    List<List<String>> findUsernameCaseConflictGroups();

    void save();

    @Override
    default void close() {
    }
}
