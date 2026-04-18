package team.kitemc.verifymc.audit;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
        return Arrays.stream(values())
                .filter(value -> value.key.equals(normalized))
                .findFirst();
    }

    public static List<String> availableKeys() {
        return Arrays.stream(values())
                .map(AuditEventType::key)
                .toList();
    }
}
