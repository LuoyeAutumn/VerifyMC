package team.kitemc.verifymc.audit;

public interface AuditRepository extends AutoCloseable {
    void append(AuditEntry entry);

    AuditPage query(AuditQuery query);

    @Override
    default void close() {
        // Default: no-op
    }
}
