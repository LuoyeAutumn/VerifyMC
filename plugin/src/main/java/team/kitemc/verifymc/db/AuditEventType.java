package team.kitemc.verifymc.db;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public enum AuditEventType {
    APPROVE("approve", "Approve"),
    REJECT("reject", "Reject"),
    BAN("ban", "Ban"),
    UNBAN("unban", "Unban"),
    DELETE("delete", "Delete"),
    PASSWORD_CHANGE("password_change", "Password change"),
    PASSWORD_MIGRATION("password_migration", "Password migration"),
    EMAIL_UPDATE("email_update", "Email update"),
    ADMIN_ACCESS_DENIED("admin_access_denied", "Admin access denied"),
    SMS_SEND_SUCCESS("sms_send_success", "SMS verification code sent successfully"),
    SMS_SEND_FAILED("sms_send_failed", "SMS verification code sending failed"),
    EMAIL_SEND_SUCCESS("email_send_success", "Email verification code sent successfully"),
    EMAIL_SEND_FAILED("email_send_failed", "Email verification code sending failed");

    private final String key;
    private final String description;

    AuditEventType(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
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
