package team.kitemc.verifymc.core;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.security.AdminAuthMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Type-safe configuration access, eliminating scattered getConfig().getString(...)
 * calls with magic strings throughout the codebase.
 */
public class ConfigManager {
    private final JavaPlugin plugin;

    private static final List<String> DEFAULT_EMAIL_DOMAIN_WHITELIST = Arrays.asList(
        "gmail.com", "qq.com", "163.com", "126.com", "outlook.com", "hotmail.com", "yahoo.com",
        "sina.com", "aliyun.com", "foxmail.com", "icloud.com", "yeah.net", "live.com", "mail.com",
        "protonmail.com", "zoho.com"
    );

    private static final Set<String> VALID_STORAGE_TYPES = new HashSet<>(Arrays.asList("file", "mysql"));
    private static final Set<String> VALID_AUTH_METHODS = new HashSet<>(Arrays.asList("email", "captcha"));
    private static final Set<String> VALID_LOGIN_METHODS = new HashSet<>(Arrays.asList("username", "email", "phone"));
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        migrateAuthMethodsConfig();
        migrateEmailConfig();
        validateConfig();
    }

    private void migrateAuthMethodsConfig() {
        boolean hasOldAuthMethods = getConfig().contains("auth_methods");
        boolean hasOldAuth = getConfig().contains("auth.must_auth_methods") || getConfig().contains("auth.option_auth_methods");
        boolean hasNewAuth = getConfig().contains("register.auth.must_auth_methods");

        // 迁移最旧的 auth_methods 到 register.auth
        if (hasOldAuthMethods && !hasOldAuth && !hasNewAuth) {
            List<String> oldMethods = getConfig().getStringList("auth_methods");
            if (oldMethods != null && !oldMethods.isEmpty()) {
                plugin.getLogger().log(Level.INFO, "Migrating auth_methods to register.auth.must_auth_methods config...");
                getConfig().set("register.auth.must_auth_methods", oldMethods);
                getConfig().set("register.auth.option_auth_methods", new ArrayList<>());
                getConfig().set("register.auth.min_option_auth_methods", 0);
                getConfig().set("auth_methods", null);
                plugin.saveConfig();
                plugin.reloadConfig();
                plugin.getLogger().log(Level.INFO, "Migration completed. Old auth_methods migrated to register.auth.must_auth_methods");
            }
        }

        // 迁移旧的 auth.* 到 register.auth.*
        if (hasOldAuth && !hasNewAuth) {
            plugin.getLogger().log(Level.INFO, "Migrating auth.* to register.auth.* config...");
            List<String> mustMethods = getConfig().getStringList("auth.must_auth_methods");
            List<String> optionMethods = getConfig().getStringList("auth.option_auth_methods");
            int minOption = getConfig().getInt("auth.min_option_auth_methods", 0);

            if (mustMethods != null && !mustMethods.isEmpty()) {
                getConfig().set("register.auth.must_auth_methods", mustMethods);
            }
            if (optionMethods != null) {
                getConfig().set("register.auth.option_auth_methods", optionMethods);
            }
            getConfig().set("register.auth.min_option_auth_methods", minOption);

            getConfig().set("auth", null);
            plugin.saveConfig();
            plugin.reloadConfig();
            plugin.getLogger().log(Level.INFO, "Migration completed. auth.* migrated to register.auth.*");
        }
    }

    private void migrateEmailConfig() {
        boolean hasOldSmtp = getConfig().contains("smtp.host");
        boolean hasNewSmtp = getConfig().contains("email.smtp.host");
        boolean hasOldUserNotification = getConfig().contains("user_notification.enabled");
        boolean hasNewNotification = getConfig().contains("email.notification.enabled");

        if (hasOldSmtp && !hasNewSmtp) {
            plugin.getLogger().log(Level.INFO, "Migrating email config to new format...");

            if (getConfig().contains("smtp.host")) {
                getConfig().set("email.smtp.host", getConfig().getString("smtp.host"));
            }
            if (getConfig().contains("smtp.port")) {
                getConfig().set("email.smtp.port", getConfig().getInt("smtp.port"));
            }
            if (getConfig().contains("smtp.username")) {
                getConfig().set("email.smtp.username", getConfig().getString("smtp.username"));
            }
            if (getConfig().contains("smtp.password")) {
                getConfig().set("email.smtp.password", getConfig().getString("smtp.password"));
            }
            if (getConfig().contains("smtp.from")) {
                getConfig().set("email.smtp.from", getConfig().getString("smtp.from"));
            }
            if (getConfig().contains("smtp.enable_ssl")) {
                getConfig().set("email.smtp.enable_ssl", getConfig().getBoolean("smtp.enable_ssl"));
            }

            if (getConfig().contains("email_subject")) {
                getConfig().set("email.subject", getConfig().getString("email_subject"));
            }
            if (getConfig().contains("max_accounts_per_email")) {
                getConfig().set("email.max_accounts_per_email", getConfig().getInt("max_accounts_per_email"));
            }
            if (getConfig().contains("enable_email_domain_whitelist")) {
                getConfig().set("email.domain_whitelist_enabled", getConfig().getBoolean("enable_email_domain_whitelist"));
            }
            if (getConfig().contains("enable_email_alias_limit")) {
                getConfig().set("email.alias_limit_enabled", getConfig().getBoolean("enable_email_alias_limit"));
            }
            if (getConfig().contains("email_domain_whitelist")) {
                getConfig().set("email.domain_whitelist", getConfig().getStringList("email_domain_whitelist"));
            }

            getConfig().set("smtp", null);
            getConfig().set("email_subject", null);
            getConfig().set("max_accounts_per_email", null);
            getConfig().set("enable_email_domain_whitelist", null);
            getConfig().set("enable_email_alias_limit", null);
            getConfig().set("email_domain_whitelist", null);

            plugin.saveConfig();
            plugin.reloadConfig();
            plugin.getLogger().log(Level.INFO, "Email config migration completed.");
        }

        if (hasOldUserNotification && !hasNewNotification) {
            plugin.getLogger().log(Level.INFO, "Migrating user_notification to email.notification...");

            if (getConfig().contains("user_notification.enabled")) {
                getConfig().set("email.notification.enabled", getConfig().getBoolean("user_notification.enabled"));
            }
            if (getConfig().contains("user_notification.on_approve")) {
                getConfig().set("email.notification.on_approve", getConfig().getBoolean("user_notification.on_approve"));
            }
            if (getConfig().contains("user_notification.on_reject")) {
                getConfig().set("email.notification.on_reject", getConfig().getBoolean("user_notification.on_reject"));
            }

            getConfig().set("user_notification", null);

            plugin.saveConfig();
            plugin.reloadConfig();
            plugin.getLogger().log(Level.INFO, "User notification migration completed.");
        }
    }

    /**
     * Validates configuration values and logs warnings for invalid settings.
     */
    private void validateConfig() {
        String adminAuthMode = getConfig().getString("admin_auth.mode", "op");
        String effectiveAuthMode;
        if (!"op".equalsIgnoreCase(adminAuthMode) && !"permission".equalsIgnoreCase(adminAuthMode)) {
            plugin.getLogger().log(Level.WARNING,
                "Invalid admin_auth.mode: {0}. Must be one of: op, permission. Using default ''op''.",
                adminAuthMode);
            effectiveAuthMode = "op";
        } else {
            effectiveAuthMode = adminAuthMode.toLowerCase();
        }
        plugin.getLogger().log(Level.INFO, "Admin auth mode: {0}", effectiveAuthMode);

        // Validate web port
        int webPort = getWebPort();
        if (webPort < MIN_PORT || webPort > MAX_PORT) {
            plugin.getLogger().log(Level.WARNING,
                "Invalid web_port: {0}. Must be between {1} and {2}. Using default 8080.",
                new Object[]{webPort, MIN_PORT, MAX_PORT});
        }

        // Validate WebSocket port
        int wsPort = getWsPort();
        if (wsPort < MIN_PORT || wsPort > MAX_PORT) {
            plugin.getLogger().log(Level.WARNING,
                "Invalid ws_port: {0}. Must be between {1} and {2}. Using default 8081.",
                new Object[]{wsPort, MIN_PORT, MAX_PORT});
        }

        if (isSslEnabled()) {
            validateSslKeystorePath();

            if (getSslKeystoreType().isEmpty()) {
                plugin.getLogger().warning("SSL is enabled but ssl.keystore.type is empty. Using default PKCS12.");
            }

            if (getSslKeystorePassword().isEmpty()) {
                plugin.getLogger().warning("SSL is enabled and ssl.keystore.password is empty. Make sure the keystore intentionally uses an empty password.");
            }
        }

        // Validate storage type
        String storageType = getStorageType();
        if (!VALID_STORAGE_TYPES.contains(storageType.toLowerCase())) {
            plugin.getLogger().log(Level.WARNING,
                "Invalid storage type: {0}. Must be one of: {1}. Using default ''file''.",
                new Object[]{storageType, String.join(", ", VALID_STORAGE_TYPES)});
        }

        // Validate MySQL port if using MySQL storage
        if ("mysql".equalsIgnoreCase(storageType)) {
            int mysqlPort = getConfig().getInt("mysql.port", 3306);
            if (mysqlPort < MIN_PORT || mysqlPort > MAX_PORT) {
                plugin.getLogger().log(Level.WARNING,
                    "Invalid mysql.port: {0}. Must be between {1} and {2}. Using default 3306.",
                    new Object[]{mysqlPort, MIN_PORT, MAX_PORT});
            }
        }

        validateAuthMethodsConfig();

        plugin.getLogger().log(Level.INFO, "Configuration validated successfully");
    }

    private void validateAuthMethodsConfig() {
        List<String> mustMethods = getMustAuthMethodsRaw();
        List<String> optionMethods = getOptionAuthMethodsRaw();
        Set<String> conflictMethods = new HashSet<>();

        for (String method : mustMethods) {
            if (!VALID_AUTH_METHODS.contains(method.toLowerCase())) {
                plugin.getLogger().log(Level.WARNING,
                    "Unsupported auth method in must_auth_methods: {0}. Supported methods: {1}. Ignoring.",
                    new Object[]{method, String.join(", ", VALID_AUTH_METHODS)});
            }
        }

        for (String method : optionMethods) {
            if (!VALID_AUTH_METHODS.contains(method.toLowerCase())) {
                plugin.getLogger().log(Level.WARNING,
                    "Unsupported auth method in option_auth_methods: {0}. Supported methods: {1}. Ignoring.",
                    new Object[]{method, String.join(", ", VALID_AUTH_METHODS)});
            }
            if (mustMethods.contains(method)) {
                conflictMethods.add(method);
            }
        }

        for (String method : conflictMethods) {
            plugin.getLogger().log(Level.WARNING,
                "Auth method ''{0}'' is in both must_auth_methods and option_auth_methods. Treating as required.",
                method);
        }

        int minOption = getMinOptionAuthMethods();
        int validOptionCount = optionMethods.size();
        if (minOption > validOptionCount) {
            plugin.getLogger().log(Level.WARNING,
                "min_option_auth_methods ({0}) is greater than option_auth_methods count ({1}). Adjusting to {1}.",
                new Object[]{minOption, validOptionCount});
        }
    }

    private List<String> getMustAuthMethodsRaw() {
        List<String> methods = getConfig().getStringList("register.auth.must_auth_methods");
        if (methods == null || methods.isEmpty()) {
            methods = getConfig().getStringList("auth.must_auth_methods");
        }
        return methods != null ? methods : Collections.emptyList();
    }

    private List<String> getOptionAuthMethodsRaw() {
        List<String> methods = getConfig().getStringList("register.auth.option_auth_methods");
        if (methods == null || methods.isEmpty()) {
            methods = getConfig().getStringList("auth.option_auth_methods");
        }
        return methods != null ? methods : Collections.emptyList();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
    }

    // --- General config ---
    public boolean isDebug() {
        return getConfig().getBoolean("debug", false);
    }

    public AdminAuthMode getAdminAuthMode() {
        return AdminAuthMode.fromConfig(getConfig().getString("admin_auth.mode", "op"));
    }

    public boolean isAdminAuthByPermission() {
        return getAdminAuthMode() == AdminAuthMode.PERMISSION;
    }

    public String getStorageType() {
        return getConfig().getString("storage", "file");
    }

    public String getLanguage() {
        return getConfig().getString("language", "en");
    }

    // --- Web server ---
    public int getWebPort() {
        int port = getConfig().getInt("web_port", 8080);
        return (port >= MIN_PORT && port <= MAX_PORT) ? port : 8080;
    }

    public int getWsPort() {
        int port = getConfig().getInt("ws_port", 8081);
        return (port >= MIN_PORT && port <= MAX_PORT) ? port : 8081;
    }

    public String getWebServerPrefix() {
        return getConfig().getString("web_server_prefix", "[VerifyMC]");
    }

    public boolean isSslEnabled() {
        return getConfig().getBoolean("ssl.enabled", false);
    }

    public String getSslKeystorePath() {
        return getConfig().getString("ssl.keystore.path", "").trim();
    }

    public String getSslKeystorePassword() {
        return getConfig().getString("ssl.keystore.password", "");
    }

    public String getSslKeystoreType() {
        return getConfig().getString("ssl.keystore.type", "PKCS12").trim();
    }

    public Path resolveSslKeystorePath() throws IOException {
        return team.kitemc.verifymc.web.ServerSslContextFactory.resolveKeystorePath(
                plugin.getDataFolder().toPath(),
                getSslKeystorePath());
    }

    private void validateSslKeystorePath() {
        try {
            resolveSslKeystorePath();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "SSL is enabled but {0}", e.getMessage());
        }
    }

    // --- Frontend ---
    public String getTheme() {
        return getConfig().getString("frontend.theme", "glassx");
    }

    public String getLogoUrl() {
        return getConfig().getString("frontend.logo_url", "");
    }

    public String getAnnouncement() {
        return getConfig().getString("frontend.announcement", "");
    }

    public boolean isServeStaticEnabled() {
        return getConfig().getBoolean("frontend.serve_static", true);
    }

    public List<String> getAllowedOrigins() {
        List<String> origins = getConfig().getStringList("frontend.allowed_origins");
        if (origins == null || origins.isEmpty()) {
            return Collections.emptyList();
        }
        return origins.stream()
            .map(origin -> origin == null ? "" : origin.trim())
            .filter(origin -> !origin.isEmpty())
            .toList();
    }

    public String getUsernameRegex() {
        return getConfig().getString("username_regex", "^[a-zA-Z0-9_-]{3,16}$");
    }

    public boolean isUsernameCaseSensitive() {
        return getConfig().getBoolean("username_case_sensitive", false);
    }

    // --- Auth methods ---
    public List<String> getAuthMethods() {
        List<String> newMethods = getMustAuthMethods();
        if (!newMethods.isEmpty()) {
            return newMethods;
        }
        return getConfig().getStringList("auth_methods");
    }

    public List<String> getMustAuthMethods() {
        List<String> methods = getConfig().getStringList("register.auth.must_auth_methods");
        if (methods == null || methods.isEmpty()) {
            methods = getConfig().getStringList("auth.must_auth_methods");
        }
        if (methods == null || methods.isEmpty()) {
            return Collections.singletonList("email");
        }
        return methods.stream()
            .filter(method -> VALID_AUTH_METHODS.contains(method.toLowerCase()))
            .toList();
    }

    public List<String> getOptionAuthMethods() {
        List<String> methods = getConfig().getStringList("register.auth.option_auth_methods");
        if (methods == null || methods.isEmpty()) {
            methods = getConfig().getStringList("auth.option_auth_methods");
        }
        if (methods == null || methods.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> mustMethods = getMustAuthMethodsRaw();
        return methods.stream()
            .filter(method -> VALID_AUTH_METHODS.contains(method.toLowerCase()))
            .filter(method -> !mustMethods.contains(method))
            .toList();
    }

    public int getMinOptionAuthMethods() {
        int minOption = getConfig().getInt("register.auth.min_option_auth_methods", -1);
        if (minOption == -1) {
            minOption = getConfig().getInt("auth.min_option_auth_methods", 0);
        }
        int maxOption = getOptionAuthMethods().size();
        return Math.max(0, Math.min(minOption, maxOption));
    }

    public boolean isAuthMethodRequired(String method) {
        if (method == null || method.isEmpty()) {
            return false;
        }
        return getMustAuthMethods().stream()
            .anyMatch(m -> m.equalsIgnoreCase(method));
    }

    public boolean isAuthMethodOptional(String method) {
        if (method == null || method.isEmpty()) {
            return false;
        }
        return getOptionAuthMethods().stream()
            .anyMatch(m -> m.equalsIgnoreCase(method));
    }

    public boolean isEmailAuthEnabled() {
        return isAuthMethodRequired("email") || isAuthMethodOptional("email");
    }

    public boolean isCaptchaAuthEnabled() {
        return isAuthMethodRequired("captcha") || isAuthMethodOptional("captcha");
    }

    // --- Email ---
    public String getEmailSubject() {
        String subject = getConfig().getString("email.subject");
        if (subject == null || subject.isEmpty()) {
            subject = getConfig().getString("email_subject", "VerifyMC Verification Code");
        }
        return subject;
    }

    public boolean isEmailDomainWhitelistEnabled() {
        if (getConfig().contains("email.domain_whitelist_enabled")) {
            return getConfig().getBoolean("email.domain_whitelist_enabled", true);
        }
        return getConfig().getBoolean("enable_email_domain_whitelist", true);
    }

    public List<String> getEmailDomainWhitelist() {
        List<String> list = null;
        try {
            list = getConfig().getStringList("email.domain_whitelist");
            if (list == null || list.isEmpty()) {
                list = getConfig().getStringList("email_domain_whitelist");
            }
        } catch (Exception ignored) {}
        if (list == null || list.isEmpty()) {
            return DEFAULT_EMAIL_DOMAIN_WHITELIST;
        }
        return list;
    }

    public boolean isEmailAliasLimitEnabled() {
        if (getConfig().contains("email.alias_limit_enabled")) {
            return getConfig().getBoolean("email.alias_limit_enabled", false);
        }
        return getConfig().getBoolean("enable_email_alias_limit", false);
    }

    public int getMaxAccountsPerEmail() {
        int max = getConfig().getInt("email.max_accounts_per_email", -1);
        if (max == -1) {
            max = getConfig().getInt("max_accounts_per_email", 2);
        }
        return max;
    }

    // --- SMTP ---
    public String getSmtpHost() {
        String host = getConfig().getString("email.smtp.host");
        if (host == null || host.isEmpty()) {
            host = getConfig().getString("smtp.host", "smtp.qq.com");
        }
        return host;
    }

    public int getSmtpPort() {
        int port = getConfig().getInt("email.smtp.port", -1);
        if (port == -1) {
            port = getConfig().getInt("smtp.port", 587);
        }
        return port;
    }

    public String getSmtpUsername() {
        String username = getConfig().getString("email.smtp.username");
        if (username == null || username.isEmpty()) {
            username = getConfig().getString("smtp.username", "");
        }
        return username;
    }

    public String getSmtpPassword() {
        String password = getConfig().getString("email.smtp.password");
        if (password == null || password.isEmpty()) {
            password = getConfig().getString("smtp.password", "");
        }
        return password;
    }

    public String getSmtpFrom() {
        String from = getConfig().getString("email.smtp.from");
        if (from == null || from.isEmpty()) {
            from = getConfig().getString("smtp.from", getSmtpUsername());
        }
        return from;
    }

    public boolean isSmtpEnableSsl() {
        if (getConfig().contains("email.smtp.enable_ssl")) {
            return getConfig().getBoolean("email.smtp.enable_ssl", true);
        }
        return getConfig().getBoolean("smtp.enable_ssl", true);
    }

    // --- Email Notification ---
    public boolean isEmailNotificationEnabled() {
        if (getConfig().contains("email.notification.enabled")) {
            return getConfig().getBoolean("email.notification.enabled", true);
        }
        return getConfig().getBoolean("user_notification.enabled", true);
    }

    public boolean isNotifyOnApprove() {
        if (getConfig().contains("email.notification.on_approve")) {
            return getConfig().getBoolean("email.notification.on_approve", true);
        }
        return getConfig().getBoolean("user_notification.on_approve", true);
    }

    public boolean isNotifyOnReject() {
        if (getConfig().contains("email.notification.on_reject")) {
            return getConfig().getBoolean("email.notification.on_reject", true);
        }
        return getConfig().getBoolean("user_notification.on_reject", true);
    }

    // --- Whitelist ---
    public String getWhitelistMode() {
        return getConfig().getString("whitelist_mode", "bukkit");
    }

    // --- Auto Update Resources ---
    public boolean isAutoUpdateResources() {
        return getConfig().getBoolean("auto_update_resources", true);
    }

    // --- AuthMe ---
    public boolean isAuthmeEnabled() {
        return getConfig().getBoolean("authme.enabled", false);
    }

    public String getAuthmePasswordRegex() {
        return getConfig().getString("authme.password_regex", "^[a-zA-Z0-9_]{8,26}$");
    }

    public int getAuthmeSyncInterval() {
        return getConfig().getInt("authme.database.sync_interval_seconds", 30);
    }

    // --- Captcha ---
    public String getCaptchaType() {
        return getConfig().getString("captcha.type", "math");
    }

    // --- Bedrock ---
    public boolean isBedrockEnabled() {
        return getConfig().getBoolean("bedrock.enabled", false);
    }

    public String getBedrockPrefix() {
        return getConfig().getString("bedrock.prefix", ".");
    }

    // --- Registration ---
    public boolean isAutoApprove() {
        return getConfig().getBoolean("register.auto_approve", false);
    }

    // --- Forgot Password ---
    public boolean isForgotPasswordEnabled() {
        return getConfig().getBoolean("forgot_password.enabled", true);
    }

    public boolean isForgotPasswordCaptchaEnabled() {
        return getConfig().getBoolean("forgot_password.captcha_enabled", false);
    }

    public List<String> getPasswordResetMethods() {
        List<String> methods = getConfig().getStringList("user.password_reset_methods");
        if (methods.isEmpty()) {
            methods = java.util.Arrays.asList("current_password");
        }
        return methods;
    }

    // --- Questionnaire rate limit ---
    public int getQuestionnaireRateLimitIpMax() {
        return getConfig().getInt("questionnaire.rate_limit.ip.max", 20);
    }

    public int getQuestionnaireRateLimitUuidMax() {
        return getConfig().getInt("questionnaire.rate_limit.uuid.max", 8);
    }

    public int getQuestionnaireRateLimitEmailMax() {
        return getConfig().getInt("questionnaire.rate_limit.email.max", 6);
    }

    public long getQuestionnaireRateLimitWindowMs() {
        return getConfig().getLong("questionnaire.rate_limit.window_ms", 300000L);
    }

    // --- MySQL config ---
    public java.util.Properties getMysqlProperties() {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("host", getConfig().getString("mysql.host", "localhost"));
        props.setProperty("port", String.valueOf(getConfig().getInt("mysql.port", 3306)));
        props.setProperty("database", getConfig().getString("mysql.database", "verifymc"));
        props.setProperty("user", getConfig().getString("mysql.user", "root"));
        props.setProperty("password", getConfig().getString("mysql.password", ""));
        props.setProperty("useSSL", String.valueOf(getMysqlUseSSL()));
        props.setProperty("allowPublicKeyRetrieval", String.valueOf(getMysqlAllowPublicKeyRetrieval()));
        return props;
    }

    /**
     * Get MySQL SSL setting. Default is true for security.
     */
    public boolean getMysqlUseSSL() {
        return getConfig().getBoolean("mysql.useSSL", true);
    }

    /**
     * Get MySQL allowPublicKeyRetrieval setting. Default is false for security.
     * Enable this if you need to connect to MySQL 8.0+ with default authentication.
     */
    public boolean getMysqlAllowPublicKeyRetrieval() {
        return getConfig().getBoolean("mysql.allowPublicKeyRetrieval", false);
    }

    public java.util.List<java.util.Map<String, Object>> getDownloadResources() {
        java.util.List<java.util.Map<String, Object>> resources = new java.util.ArrayList<>();
        org.bukkit.configuration.ConfigurationSection section = getConfig().getConfigurationSection("downloads");
        if (section == null) {
            return resources;
        }
        for (String key : section.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection resourceSection = section.getConfigurationSection(key);
            if (resourceSection != null) {
                java.util.Map<String, Object> resource = new java.util.HashMap<>();
                resource.put("id", key);
                resource.put("name", resourceSection.getString("name", key));
                resource.put("description", resourceSection.getString("description", ""));
                resource.put("version", resourceSection.getString("version", ""));
                resource.put("size", resourceSection.getString("size", ""));
                resource.put("url", resourceSection.getString("url", ""));
                resource.put("icon", resourceSection.getString("icon", "package"));
                resources.add(resource);
            }
        }
        return resources;
    }

    // --- SMS ---
    public String getSmsProvider() {
        return getConfig().getString("sms.provider", "tencent");
    }

    public String getSmsSecretId() {
        return getConfig().getString("sms.tencent.secret_id", "");
    }

    public String getSmsSecretKey() {
        return getConfig().getString("sms.tencent.secret_key", "");
    }

    public String getSmsAccessKeyId() {
        return getConfig().getString("sms.aliyun.access_key_id", "");
    }

    public String getSmsAccessKeySecret() {
        return getConfig().getString("sms.aliyun.access_key_secret", "");
    }

    public String getSmsSdkAppId() {
        return getConfig().getString("sms.sdk_app_id", "");
    }

    public String getSmsSignName() {
        return getConfig().getString("sms.sign_name", "");
    }

    public String getSmsTemplateId() {
        return getConfig().getString("sms.template_id", "");
    }

    public String getSmsRegion() {
        return getConfig().getString("sms.region", "ap-guangzhou");
    }

    public List<String> getCountryCodes() {
        List<String> codes = getConfig().getStringList("sms.country_codes");
        if (codes == null || codes.isEmpty()) {
            return Collections.singletonList("+86");
        }
        return codes;
    }

    public String getSmsPhoneRegex() {
        return getConfig().getString("sms.phone_regex", "^[0-9]{6,15}$");
    }

    public int getMaxAccountsPerPhone() {
        return getConfig().getInt("sms.max_accounts_per_phone", 5);
    }

    public boolean isSmsEnabled() {
        return getConfig().getBoolean("sms.enabled", false);
    }

    // --- Login Methods ---
    public List<String> getAllowedLoginMethods() {
        List<String> methods = getConfig().getStringList("login.allowed_methods");
        if (methods == null || methods.isEmpty()) {
            return Arrays.asList("username", "email", "phone");
        }
        return methods.stream()
            .filter(method -> VALID_LOGIN_METHODS.contains(method.toLowerCase()))
            .map(String::toLowerCase)
            .toList();
    }

    public boolean isLoginMethodAllowed(String method) {
        if (method == null || method.isEmpty()) {
            return false;
        }
        return getAllowedLoginMethods().stream()
            .anyMatch(m -> m.equalsIgnoreCase(method));
    }

    public boolean isEmailLoginEnabled() {
        return isLoginMethodAllowed("email");
    }

    public boolean isPhoneLoginEnabled() {
        return isLoginMethodAllowed("phone") && isSmsEnabled();
    }

    public boolean isUsernameLoginEnabled() {
        return isLoginMethodAllowed("username");
    }

    public int getLoginCodeExpireSeconds() {
        return getConfig().getInt("login.code_expire_seconds", 300);
    }

    public int getLoginCodeLength() {
        int length = getConfig().getInt("login.code_length", 6);
        return Math.max(4, Math.min(length, 8));
    }
}
