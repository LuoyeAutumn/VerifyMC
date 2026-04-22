package team.kitemc.verifymc.util;

import java.util.Map;
import java.util.regex.Pattern;
import team.kitemc.verifymc.core.ConfigManager;
import team.kitemc.verifymc.db.UserDao;

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

    public boolean canOperateAdminTarget(String username, UserDao userDao) {
        String trimmed = normalizeWhitespace(username);
        if (trimmed.isEmpty()) {
            return false;
        }
        if (isValidForAdminTarget(trimmed)) {
            return true;
        }
        if (!LEGACY_ADMIN_USERNAME_PATTERN.matcher(trimmed).matches()) {
            return false;
        }
        return findExistingUser(userDao, trimmed) != null;
    }

    public String resolveAdminTarget(String username, UserDao userDao) {
        String trimmed = normalizeWhitespace(username);
        if (trimmed.isEmpty()) {
            return "";
        }

        Map<String, Object> existingUser = findExistingUser(userDao, trimmed);
        if (!isValidForAdminTarget(trimmed)) {
            if (!LEGACY_ADMIN_USERNAME_PATTERN.matcher(trimmed).matches()) {
                return "";
            }
            return existingUser != null ? (String) existingUser.get("username") : "";
        }

        String normalized = normalizeAdminTarget(trimmed);
        Map<String, Object> normalizedUser = findExistingUser(userDao, normalized);
        if (normalizedUser != null) {
            return (String) normalizedUser.get("username");
        }

        Map<String, Object> bedrockVariantUser = findBedrockVariantUser(userDao, trimmed, normalized);
        if (bedrockVariantUser != null) {
            return (String) bedrockVariantUser.get("username");
        }

        return normalized;
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

    private Map<String, Object> findBedrockVariantUser(
            UserDao userDao,
            String originalUsername,
            String normalizedUsername
    ) {
        if (!configManager.isBedrockEnabled() || looksLikeBedrockStoredUsername(originalUsername)) {
            return null;
        }

        String rawUsername = extractValidationUsernameForAdminTarget(originalUsername);
        if (rawUsername.isEmpty()) {
            return null;
        }

        String prefixedUsername = getBedrockPrefix() + rawUsername;
        if (prefixedUsername.equals(normalizedUsername)) {
            return null;
        }
        return findExistingUser(userDao, prefixedUsername);
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

    private Map<String, Object> findExistingUser(UserDao userDao, String username) {
        Map<String, Object> exact = userDao.getUserByUsernameExact(username);
        if (exact != null) {
            return exact;
        }
        return userDao.getUserByUsername(username);
    }
}
