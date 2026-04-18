package team.kitemc.verifymc.user;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum PasswordResetMethod {
    CURRENT_PASSWORD("current_password"),
    EMAIL_CODE("email_code");

    private final String key;

    PasswordResetMethod(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Set<PasswordResetMethod> sanitize(List<String> rawMethods) {
        LinkedHashSet<PasswordResetMethod> resolved = new LinkedHashSet<>();
        if (rawMethods != null) {
            for (String rawMethod : rawMethods) {
                if (rawMethod == null || rawMethod.isBlank()) {
                    continue;
                }
                String normalized = rawMethod.trim().toLowerCase(Locale.ROOT);
                Arrays.stream(values())
                        .filter(method -> method.key.equals(normalized))
                        .findFirst()
                        .ifPresent(resolved::add);
            }
        }
        if (resolved.isEmpty()) {
            resolved.add(CURRENT_PASSWORD);
        }
        return resolved;
    }
}
