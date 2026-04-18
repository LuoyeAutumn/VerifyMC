package team.kitemc.verifymc.audit;

import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;

public class MysqlAuditDao implements AuditDao {
    private final Connection connection;
    private final Plugin plugin;

    public MysqlAuditDao(Properties mysqlConfig, Plugin plugin) throws SQLException {
        this.plugin = plugin;
        String useSSL = mysqlConfig.getProperty("useSSL", "true");
        String allowPublicKeyRetrieval = mysqlConfig.getProperty("allowPublicKeyRetrieval", "false");
        String url = "jdbc:mysql://" + mysqlConfig.getProperty("host") + ":" +
                mysqlConfig.getProperty("port") + "/" +
                mysqlConfig.getProperty("database") +
                "?useSSL=" + useSSL +
                "&allowPublicKeyRetrieval=" + allowPublicKeyRetrieval +
                "&characterEncoding=utf8";
        this.connection = DriverManager.getConnection(url, mysqlConfig.getProperty("user"), mysqlConfig.getProperty("password"));
        createTableIfNeeded();
    }

    private void createTableIfNeeded() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS audit_entries (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "event_type VARCHAR(64) NOT NULL," +
                    "operator_name VARCHAR(64) NOT NULL," +
                    "target_name VARCHAR(64) NOT NULL," +
                    "detail TEXT NOT NULL," +
                    "occurred_at BIGINT NOT NULL," +
                    "INDEX idx_audit_entries_event_time (event_type, occurred_at)," +
                    "INDEX idx_audit_entries_occurred_at (occurred_at))");
        }
    }

    @Override
    public void addAudit(AuditRecord audit) {
        String sql = "INSERT INTO audit_entries (event_type, operator_name, target_name, detail, occurred_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, audit.eventType().key());
            statement.setString(2, audit.operator());
            statement.setString(3, audit.target());
            statement.setString(4, audit.detail());
            statement.setLong(5, audit.occurredAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to append audit entry", e);
        }
    }

    @Override
    public AuditPage query(AuditQuery query) {
        try {
            List<Object> countParams = new ArrayList<>();
            String whereClause = buildWhereClause(query, countParams);
            long totalCount = queryTotal(whereClause, countParams);

            List<Object> pageParams = new ArrayList<>();
            String pageWhereClause = buildWhereClause(query, pageParams);
            List<AuditRecord> items = queryItems(pageWhereClause, pageParams, query);
            return new AuditPage(items, query.page(), query.size(), totalCount);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to query audit entries", e);
            return new AuditPage(List.of(), query.page(), query.size(), 0);
        }
    }

    private long queryTotal(String whereClause, List<Object> params) throws SQLException {
        String sql = "SELECT COUNT(*) FROM audit_entries" + whereClause;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }
        return 0;
    }

    private List<AuditRecord> queryItems(String whereClause, List<Object> params, AuditQuery query) throws SQLException {
        String sql = "SELECT id, event_type, operator_name, target_name, detail, occurred_at " +
                "FROM audit_entries" + whereClause +
                " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?";
        List<AuditRecord> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            List<Object> allParams = new ArrayList<>(params);
            allParams.add(query.size());
            allParams.add((query.page() - 1) * query.size());
            bindParams(statement, allParams);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(new AuditRecord(
                            resultSet.getLong("id"),
                            parseEventType(resultSet.getString("event_type")),
                            resultSet.getString("operator_name"),
                            resultSet.getString("target_name"),
                            resultSet.getString("detail"),
                            resultSet.getLong("occurred_at")
                    ));
                }
            }
        }
        return items;
    }

    private AuditEventType parseEventType(String eventType) throws SQLException {
        return AuditEventType.fromKey(eventType)
                .orElseThrow(() -> new SQLException("Unsupported audit event type: " + eventType));
    }

    private String buildWhereClause(AuditQuery query, List<Object> params) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
        if (query.eventType() != null) {
            whereClause.append(" AND event_type = ?");
            params.add(query.eventType().key());
        }

        if (!query.keyword().isBlank()) {
            String likeKeyword = "%" + query.keyword().toLowerCase(Locale.ROOT) + "%";
            whereClause.append(" AND (LOWER(operator_name) LIKE ? OR LOWER(target_name) LIKE ? OR LOWER(detail) LIKE ?)");
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
        }
        return whereClause.toString();
    }

    private void bindParams(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int index = 0; index < params.size(); index++) {
            statement.setObject(index + 1, params.get(index));
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to close audit DAO connection", e);
        }
    }
}

