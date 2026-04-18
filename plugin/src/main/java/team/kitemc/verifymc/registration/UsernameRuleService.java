package team.kitemc.verifymc.registration;

import java.util.Optional;
import java.util.regex.Pattern;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.user.UserRecord;
import team.kitemc.verifymc.user.UserRepository;

public class UsernameRuleService {
    private static final Pattern LEGACY_ADMIN_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.\\-\\s]{1,32}$");

    private final ConfigManager configManager;

    public UsernameRuleService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public String getUnifiedRegex() {
        return configManager.getUsernameRegex();
    }

    public String getBedrockPrefix() {
        return configManager.getBedrockPrefix();
    }

    public boolean isValid(String username, String platform) {
        return matchesUnifiedRegex(extractValidationUsername(username, platform));
    }

    public boolean isValidForAdminTarget(String username) {
        return matchesUnifiedRegex(extractValidationUsernameForAdminTarget(username));
    }

    public String normalize(String username, String platform) {
        String trimmed = normalizeWhitespace(username);
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!isBedrockPlatform(platform)) {
            return trimmed;
        }

        String rawUsername = stripBedrockPrefixes(trimmed);
        if (rawUsername.isEmpty()) {
            return "";
        }
        return getBedrockPrefix() + rawUsername;
    }

    public String extractValidationUsername(String username, String platform) {
        String trimmed = normalizeWhitespace(username);
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!isBedrockPlatform(platform)) {
            return trimmed;
        }
        return stripBedrockPrefixes(trimmed);
    }

    public String extractValidationUsernameForAdminTarget(String username) {
        String trimmed = normalizeWhitespace(username);
        if (trimmed.isEmpty()) {
            return "";
        }
        if (looksLikeBedrockStoredUsername(trimmed)) {
            return stripBedrockPrefixes(trimmed);
        }
        return trimmed;
    }

    public boolean canOperateAdminTarget(String username, UserRepository userRepository) {
        return !resolveAdminTarget(username, userRepository).isEmpty();
    }

    public String resolveAdminTarget(String username, UserRepository userRepository) {
        String trimmed = normalizeWhitespace(username);
        if (trimmed.isEmpty()) {
            return "";
        }

        Optional<UserRecord> existingUser = findExistingUser(userRepository, trimmed);
        if (!isValidForAdminTarget(trimmed)) {
            if (!LEGACY_ADMIN_USERNAME_PATTERN.matcher(trimmed).matches()) {
                return "";
            }
            return existingUser.map(UserRecord::username).orElse("");
        }

        String normalized = normalizeAdminTarget(trimmed);
        Optional<UserRecord> normalizedUser = findExistingUser(userRepository, normalized);
        if (normalizedUser.isPresent()) {
            return normalizedUser.get().username();
        }

        Optional<UserRecord> bedrockVariantUser = findBedrockVariantUser(userRepository, trimmed, normalized);
        if (bedrockVariantUser.isPresent()) {
            return bedrockVariantUser.get().username();
        }

        return normalized;
    }

    private boolean matchesUnifiedRegex(String username) {
        return !username.isEmpty() && username.matches(getUnifiedRegex());
    }

    private boolean isBedrockPlatform(String platform) {
        return configManager.isBedrockEnabled() && "bedrock".equalsIgnoreCase(platform);
    }

    private boolean looksLikeBedrockStoredUsername(String username) {
        String prefix = getBedrockPrefix();
        return configManager.isBedrockEnabled()
                && prefix != null
                && !prefix.isEmpty()
                && username.startsWith(prefix);
    }

    private String normalizeAdminTarget(String username) {
        String trimmed = normalizeWhitespace(username);
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!looksLikeBedrockStoredUsername(trimmed)) {
            return trimmed;
        }

        String rawUsername = stripBedrockPrefixes(trimmed);
        if (rawUsername.isEmpty()) {
            return "";
        }
        return getBedrockPrefix() + rawUsername;
    }

    private String stripBedrockPrefixes(String username) {
        String prefix = getBedrockPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return username;
        }

        String candidate = username;
        while (candidate.startsWith(prefix)) {
            candidate = candidate.substring(prefix.length());
        }
        return candidate;
    }

    private String normalizeWhitespace(String username) {
        return username == null ? "" : username.trim();
    }

    private Optional<UserRecord> findExistingUser(UserRepository userRepository, String username) {
        Optional<UserRecord> exact = userRepository.findByUsernameExact(username);
        if (exact.isPresent()) {
            return exact;
        }
        return userRepository.findByUsername(username);
    }

    private Optional<UserRecord> findBedrockVariantUser(
            UserRepository userRepository,
            String originalUsername,
            String normalizedUsername
    ) {
        if (!configManager.isBedrockEnabled() || looksLikeBedrockStoredUsername(originalUsername)) {
            return Optional.empty();
        }

        String rawUsername = extractValidationUsernameForAdminTarget(originalUsername);
        if (rawUsername.isEmpty()) {
            return Optional.empty();
        }

        String prefixedUsername = getBedrockPrefix() + rawUsername;
        if (prefixedUsername.equals(normalizedUsername)) {
            return Optional.empty();
        }
        return findExistingUser(userRepository, prefixedUsername);
    }
}

