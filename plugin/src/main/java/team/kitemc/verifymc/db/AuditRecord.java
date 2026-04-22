package team.kitemc.verifymc.db;

import java.util.Objects;

public record AuditRecord(
        Long id,
        AuditEventType eventType,
        String operator,
        String target,
        String detail,
        long occurredAt
) {
    public AuditRecord {
        Objects.requireNonNull(eventType, "eventType");
        operator = operator == null ? "" : operator;
        target = target == null ? "" : target;
        detail = detail == null ? "" : detail;
    }

    public AuditRecord(AuditEventType eventType, String operator, String target, String detail, long occurredAt) {
        this(null, eventType, operator, target, detail, occurredAt);
    }
}
