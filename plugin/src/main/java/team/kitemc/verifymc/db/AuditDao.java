package team.kitemc.verifymc.db;

public interface AuditDao extends AutoCloseable {
    void addAudit(AuditRecord audit);

    AuditPage query(AuditQuery query);

    default void save() {
        // Default: no-op
    }

    @Override
    default void close() {
        // Default: no-op
    }
}
