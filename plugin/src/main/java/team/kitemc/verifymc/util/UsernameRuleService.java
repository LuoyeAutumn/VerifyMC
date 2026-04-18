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
