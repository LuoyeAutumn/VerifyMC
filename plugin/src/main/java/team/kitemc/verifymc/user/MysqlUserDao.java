package team.kitemc.verifymc.user;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import org.bukkit.plugin.Plugin;
import team.kitemc.verifymc.shared.PasswordUtil;

public class MysqlUserDao implements UserRepository {
    private Connection conn;
    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;
    @SuppressWarnings("unused")
    private final ResourceBundle messages;
    private final boolean debug;
    private final boolean usernameCaseSensitive;
    private final Plugin plugin;

    public MysqlUserDao(Properties mysqlConfig, ResourceBundle messages, Plugin plugin) throws SQLException {
        this(mysqlConfig, messages, plugin, plugin.getConfig().getBoolean("username_case_sensitive", false));
    }

    public MysqlUserDao(Properties mysqlConfig, ResourceBundle messages, Plugin plugin, boolean usernameCaseSensitive) throws SQLException {
        this.messages = messages;
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        this.usernameCaseSensitive = usernameCaseSensitive;
        String useSSL = mysqlConfig.getProperty("useSSL", "true");
        String allowPublicKeyRetrieval = mysqlConfig.getProperty("allowPublicKeyRetrieval", "false");
        this.jdbcUrl = "jdbc:mysql://" + mysqlConfig.getProperty("host") + ":" +
                mysqlConfig.getProperty("port") + "/" +
                mysqlConfig.getProperty("database") +
                "?useSSL=" + useSSL +
                "&allowPublicKeyRetrieval=" + allowPublicKeyRetrieval +
                "&characterEncoding=utf8&autoReconnect=true";
        this.jdbcUser = mysqlConfig.getProperty("user");
        this.jdbcPassword = mysqlConfig.getProperty("password");
        conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
        initDatabase();
    }

    public MysqlUserDao(Properties mysqlConfig) throws SQLException {
        this(mysqlConfig, false);
    }

    public MysqlUserDao(Properties mysqlConfig, boolean usernameCaseSensitive) throws SQLException {
        this.messages = null;
        this.plugin = null;
        this.debug = false;
        this.usernameCaseSensitive = usernameCaseSensitive;
        String useSSL = mysqlConfig.getProperty("useSSL", "true");
        String allowPublicKeyRetrieval = mysqlConfig.getProperty("allowPublicKeyRetrieval", "false");
        this.jdbcUrl = "jdbc:mysql://" + mysqlConfig.getProperty("host") + ":" +
                mysqlConfig.getProperty("port") + "/" +
                mysqlConfig.getProperty("database") +
                "?useSSL=" + useSSL +
                "&allowPublicKeyRetrieval=" + allowPublicKeyRetrieval +
                "&characterEncoding=utf8&autoReconnect=true";
        this.jdbcUser = mysqlConfig.getProperty("user");
        this.jdbcPassword = mysqlConfig.getProperty("password");
        conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
        initDatabase();
    }

    private synchronized Connection getConnection() throws SQLException {
        if (conn == null || conn.isClosed() || !conn.isValid(2)) {
            debugLog("Connection lost, reconnecting...");
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException ignored) {
            }
            conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
            debugLog("Reconnected to database");
        }
        return conn;
    }

    private void initDatabase() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "username VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY," +
                    "email VARCHAR(64)," +
                    "status VARCHAR(16)," +
                    "status_before_ban VARCHAR(16) NULL," +
                    "password VARCHAR(255)," +
                    "regTime BIGINT," +
                    "discord_id VARCHAR(64)," +
                    "questionnaire_score INT NULL," +
                    "questionnaire_passed BOOLEAN NULL," +
                    "questionnaire_review_summary TEXT NULL," +
                    "questionnaire_scored_at BIGINT NULL)");

            tryAddColumn(stmt, "SELECT password FROM users LIMIT 1", "ALTER TABLE users ADD COLUMN password VARCHAR(255)");
            tryAddColumn(stmt, "SELECT regTime FROM users LIMIT 1",
                    "ALTER TABLE users ADD COLUMN regTime BIGINT",
                    "UPDATE users SET regTime = " + System.currentTimeMillis() + " WHERE regTime IS NULL");
            tryAddColumn(stmt, "SELECT status_before_ban FROM users LIMIT 1",
                    "ALTER TABLE users ADD COLUMN status_before_ban VARCHAR(16) NULL");
            tryAddColumn(stmt, "SELECT discord_id FROM users LIMIT 1", "ALTER TABLE users ADD COLUMN discord_id VARCHAR(64)");
            tryAddColumn(stmt, "SELECT questionnaire_score FROM users LIMIT 1",
                    "ALTER TABLE users ADD COLUMN questionnaire_score INT NULL");
            tryAddColumn(stmt, "SELECT questionnaire_passed FROM users LIMIT 1",
                    "ALTER TABLE users ADD COLUMN questionnaire_passed BOOLEAN NULL");
            tryAddColumn(stmt, "SELECT questionnaire_review_summary FROM users LIMIT 1",
                    "ALTER TABLE users ADD COLUMN questionnaire_review_summary TEXT NULL");
            tryAddColumn(stmt, "SELECT questionnaire_scored_at FROM users LIMIT 1",
                    "ALTER TABLE users ADD COLUMN questionnaire_scored_at BIGINT NULL");

            ensureIndex(stmt, "idx_email", "CREATE INDEX idx_email ON users(email)");
            ensureIndex(stmt, "idx_discord_id", "CREATE INDEX idx_discord_id ON users(discord_id)");
            ensureIndex(stmt, "idx_status_regtime", "CREATE INDEX idx_status_regtime ON users(status, regTime)");

            if (usernameCaseSensitive) {
                stmt.executeUpdate(
                        "ALTER TABLE users MODIFY username VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL"
                );
            }
        }
    }

    @Override
    public boolean isUsernameCaseSensitive() {
        return usernameCaseSensitive;
    }

    private void tryAddColumn(Statement stmt, String probeSql, String... ddlSqls) throws SQLException {
        try {
            stmt.executeQuery(probeSql);
        } catch (SQLException e) {
            for (String ddlSql : ddlSqls) {
                stmt.executeUpdate(ddlSql);
            }
        }
    }

    private void ensureIndex(Statement stmt, String indexName, String createIndexSql) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SHOW INDEX FROM users WHERE Key_name = '" + indexName + "'")) {
            if (!rs.next()) {
                stmt.executeUpdate(createIndexSql);
            }
        }
    }

    private void debugLog(String msg) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[DEBUG] MysqlUserDao: " + msg);
        }
    }

    @Override
    public boolean create(NewUserRecord user) {
        String sql = "INSERT IGNORE INTO users (username, email, status, status_before_ban, password, regTime, questionnaire_score, questionnaire_passed, questionnaire_review_summary, questionnaire_scored_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, user.username());
            ps.setString(2, user.email());
            ps.setString(3, user.status().value());
            bindNullableString(ps, 4, user.statusBeforeBan() == null ? null : user.statusBeforeBan().value());
            ps.setString(5, user.passwordEncoded() ? user.password() : PasswordUtil.hash(user.password()));
            ps.setLong(6, System.currentTimeMillis());
            bindNullableInteger(ps, 7, user.questionnaireScore());
            bindNullableBoolean(ps, 8, user.questionnairePassed());
            ps.setString(9, user.questionnaireReviewSummary());
            bindNullableLong(ps, 10, user.questionnaireScoredAt());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            debugLog("Error creating user: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<UserRecord> findByUsernameConfigured(String username) {
        if (usernameCaseSensitive) {
            return findByUsernameExact(username);
        }
        return findByUsernameIgnoreCase(username);
    }

    @Override
    public Optional<UserRecord> findByUsernameIgnoreCase(String username) {
        return querySingle("SELECT * FROM users WHERE LOWER(username)=LOWER(?)", username);
    }

    @Override
    public Optional<UserRecord> findByUsernameExact(String username) {
        return querySingle("SELECT * FROM users WHERE BINARY username = ?", username);
    }

    @Override
    public Optional<UserRecord> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return querySingle("SELECT * FROM users WHERE LOWER(email)=LOWER(?)", email);
    }

    @Override
    public List<UserRecord> findAllByEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return queryMany("SELECT * FROM users WHERE LOWER(email)=LOWER(?)", statement -> statement.setString(1, email));
    }

    @Override
    public Optional<UserRecord> findByDiscordId(String discordId) {
        if (discordId == null || discordId.isBlank()) {
            return Optional.empty();
        }
        return querySingle("SELECT * FROM users WHERE discord_id = ?", discordId);
    }

    @Override
    public List<UserRecord> findAll() {
        return queryMany("SELECT * FROM users", statement -> {
        });
    }

    @Override
    public List<UserSummary> findUsersByStatus(UserStatus status) {
        return queryMany(
                "SELECT * FROM users WHERE status = ? ORDER BY regTime DESC",
                statement -> statement.setString(1, status.value())
        ).stream().map(UserRecord::toSummary).toList();
    }

    @Override
    public UserPage<UserSummary> findUsers(UserQuery query) {
        List<UserSummary> items = queryMany(
                buildFindUsersSql(query, false),
                statement -> bindFindUsersParameters(statement, query, false)
        ).stream().map(UserRecord::toSummary).toList();
        long totalCount = queryCount(
                buildFindUsersSql(query, true),
                statement -> bindFindUsersParameters(statement, query, true)
        );
        return UserPage.of(items, query.page(), query.pageSize(), totalCount);
    }

    @Override
    public List<String> suggestUsernames(String prefix, int limit) {
        List<String> result = new ArrayList<>();
        String normalizedPrefix = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        String sql = "SELECT username FROM users WHERE LOWER(username) LIKE LOWER(?) ORDER BY username ASC LIMIT ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, normalizedPrefix + "%");
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            debugLog("Error suggesting usernames: " + e.getMessage());
        }
        return result;
    }

    @Override
    public long countByEmail(String email) {
        if (email == null || email.isBlank()) {
            return 0L;
        }
        return queryCount("SELECT COUNT(*) FROM users WHERE LOWER(email)=LOWER(?)", statement -> statement.setString(1, email));
    }

    @Override
    public boolean updateStatus(String username, UserStatus status, String operator) {
        return executeUpdate("UPDATE users SET status=? WHERE " + configuredUsernameMatchClause(), statement -> {
            statement.setString(1, status.value());
            statement.setString(2, username);
        });
    }

    @Override
    public boolean updateStatusForBan(String username, String operator) {
        return executeUpdate(
                "UPDATE users SET status_before_ban = CASE WHEN status = ? THEN status_before_ban ELSE status END, status = ? WHERE "
                        + configuredUsernameMatchClause(),
                statement -> {
                    statement.setString(1, UserStatus.BANNED.value());
                    statement.setString(2, UserStatus.BANNED.value());
                    statement.setString(3, username);
                }
        );
    }

    @Override
    public boolean restoreStatusFromBan(String username, UserStatus fallbackStatus, String operator) {
        UserStatus restoredStatus = fallbackStatus == null ? UserStatus.APPROVED : fallbackStatus;
        return executeUpdate(
                "UPDATE users SET status = COALESCE(status_before_ban, ?), status_before_ban = NULL WHERE "
                        + configuredUsernameMatchClause(),
                statement -> {
                    statement.setString(1, restoredStatus.value());
                    statement.setString(2, username);
                }
        );
    }

    @Override
    public boolean updatePassword(String username, String plainPassword) {
        return updateStoredPassword(username, PasswordUtil.hash(plainPassword));
    }

    @Override
    public boolean updateStoredPassword(String username, String storedPassword) {
        return executeUpdate("UPDATE users SET password=? WHERE " + configuredUsernameMatchClause(), statement -> {
            statement.setString(1, storedPassword);
            statement.setString(2, username);
        });
    }

    @Override
    public boolean updateEmail(String username, String email) {
        return executeUpdate("UPDATE users SET email=? WHERE " + configuredUsernameMatchClause(), statement -> {
            statement.setString(1, email);
            statement.setString(2, username);
        });
    }

    @Override
    public boolean updateDiscordId(String username, String discordId) {
        return executeUpdate("UPDATE users SET discord_id=? WHERE " + configuredUsernameMatchClause(), statement -> {
            statement.setString(1, discordId);
            statement.setString(2, username);
        });
    }

    @Override
    public boolean delete(String username) {
        return executeUpdate("DELETE FROM users WHERE " + configuredUsernameMatchClause(), statement -> statement.setString(1, username));
    }

    @Override
    public List<List<String>> findUsernameCaseConflictGroups() {
        return listUsernames().stream()
                .filter(username -> username != null && !username.isBlank())
                .collect(Collectors.groupingBy(
                        username -> username.toLowerCase(Locale.ROOT),
                        Collectors.toList()
                ))
                .values().stream()
                .filter(group -> group.size() > 1)
                .map(group -> group.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList())
                .sorted(java.util.Comparator.comparing(group -> group.get(0).toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Override
    public void save() {
        debugLog("MySQL storage: save() called (no-op)");
    }

    @Override
    public void close() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                debugLog("Error closing database connection: " + e.getMessage());
            }
        }
    }

    static String buildFindUsersSql(UserQuery query, boolean countOnly) {
        String select = countOnly ? "SELECT COUNT(*)" : "SELECT *";
        StringBuilder sql = new StringBuilder(select + " FROM users");
        List<String> whereClauses = new ArrayList<>();

        if (query.status() != null) {
            whereClauses.add("status = ?");
        }
        if (query.hasSearch()) {
            whereClauses.add("(LOWER(username) LIKE LOWER(?) OR LOWER(email) LIKE LOWER(?))");
        }
        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }
        if (!countOnly) {
            sql.append(" ORDER BY regTime DESC LIMIT ? OFFSET ?");
        }
        return sql.toString();
    }

    void bindFindUsersParameters(PreparedStatement statement, UserQuery query, boolean countOnly) throws SQLException {
        int index = 1;
        if (query.status() != null) {
            statement.setString(index++, query.status().value());
        }
        if (query.hasSearch()) {
            String pattern = "%" + query.search() + "%";
            statement.setString(index++, pattern);
            statement.setString(index++, pattern);
        }
        if (!countOnly) {
            statement.setInt(index++, query.pageSize());
            statement.setInt(index, query.offset());
        }
    }

    private Optional<UserRecord> querySingle(String sql, String parameter) {
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, parameter);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            debugLog("Error querying single user: " + e.getMessage());
        }
        return Optional.empty();
    }

    private List<UserRecord> queryMany(String sql, SqlBinder binder) {
        List<UserRecord> result = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            debugLog("Error querying users: " + e.getMessage());
        }
        return result;
    }

    private long queryCount(String sql, SqlBinder binder) {
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            debugLog("Error counting users: " + e.getMessage());
        }
        return 0L;
    }

    private boolean executeUpdate(String sql, SqlBinder binder) {
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            debugLog("Error executing update: " + e.getMessage());
        }
        return false;
    }

    private List<String> listUsernames() {
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT username FROM users");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            debugLog("Error listing usernames: " + e.getMessage());
        }
        return result;
    }

    private UserRecord mapUser(ResultSet rs) throws SQLException {
        Object questionnaireScore = rs.getObject("questionnaire_score");
        Object questionnairePassed = rs.getObject("questionnaire_passed");
        Object questionnaireScoredAt = rs.getObject("questionnaire_scored_at");
        return new UserRecord(
                rs.getString("username"),
                rs.getString("email"),
                UserStatus.fromValue(rs.getString("status")).orElse(UserStatus.PENDING),
                UserStatus.fromValue(rs.getString("status_before_ban")).orElse(null),
                rs.getString("password"),
                rs.getLong("regTime"),
                rs.getString("discord_id"),
                questionnaireScore == null ? null : ((Number) questionnaireScore).intValue(),
                questionnairePassed == null ? null : rs.getBoolean("questionnaire_passed"),
                rs.getString("questionnaire_review_summary"),
                questionnaireScoredAt == null ? null : ((Number) questionnaireScoredAt).longValue()
        );
    }

    private void bindNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private void bindNullableBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
        }
    }

    private void bindNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private void bindNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    @FunctionalInterface
    interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private String configuredUsernameMatchClause() {
        return usernameCaseSensitive ? "BINARY username = ?" : "LOWER(username)=LOWER(?)";
    }
}
