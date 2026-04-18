package team.kitemc.verifymc.user;

import java.util.Locale;
import java.util.Optional;

public enum UserStatus {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    BANNED("banned");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<UserStatus> fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (UserStatus status : values()) {
            if (status.value.equals(normalized)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
