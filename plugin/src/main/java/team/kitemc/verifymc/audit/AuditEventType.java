package team.kitemc.verifymc.audit;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public enum AuditEventType {
    APPROVE("approve"),
    REJECT("reject"),
    BAN("ban"),
    UNBAN("unban"),
    DELETE("delete"),
    PASSWORD_CHANGE("password_change"),
    PASSWORD_MIGRATION("password_migration"),
    EMAIL_UPDATE("email_update"),
    ADMIN_ACCESS_DENIED("admin_access_denied");

    private final String key;

    AuditEventType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<AuditEventType> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return Stream.of(values())
                .filter(eventType -> eventType.key.equals(normalized))
                .findFirst();
    }

    public static List<String> availableKeys() {
        return Stream.of(values())
                .map(AuditEventType::key)
                .toList();
    }
}

