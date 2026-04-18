package team.kitemc.verifymc.user;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.admin.AdminAccessManager;
import team.kitemc.verifymc.admin.AdminAuthMode;
import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.integration.AuthmeService;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.platform.OpsManager;
import team.kitemc.verifymc.platform.WebAuthHelper;
import team.kitemc.verifymc.platform.WebResponseHelper;
import team.kitemc.verifymc.shared.PasswordUtil;

public class LoginHandler implements HttpHandler {
    private static final int MAX_ATTEMPTS_PER_IP = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;

    private final org.bukkit.plugin.Plugin plugin;
    private final ConfigManager configManager;
    private final OpsManager opsManager;
    private final UserRepository userRepository;
    private final AuthmeService authmeService;
    private final AdminAccessManager adminAccessManager;
    private final WebAuthHelper webAuthHelper;
    private final AuditService auditService;
    private final java.util.function.BiFunction<String, String, String> messageResolver;
    private final boolean adminLogin;
    private final ConcurrentHashMap<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    private static final class LoginAttempt {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();
    }

    public LoginHandler(
            org.bukkit.plugin.Plugin plugin,
            ConfigManager configManager,
            OpsManager opsManager,
            UserRepository userRepository,
            AuthmeService authmeService,
            AdminAccessManager adminAccessManager,
            WebAuthHelper webAuthHelper,
            AuditService auditService,
            java.util.function.BiFunction<String, String, String> messageResolver,
            boolean adminLogin
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.opsManager = opsManager;
        this.userRepository = userRepository;
        this.authmeService = authmeService;
        this.adminAccessManager = adminAccessManager;
        this.webAuthHelper = webAuthHelper;
        this.auditService = auditService;
        this.messageResolver = messageResolver;
        this.adminLogin = adminLogin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) {
            return;
        }

        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (isRateLimited(clientIp)) {
            plugin.getLogger().warning("[Security] Login rate limit exceeded for IP: " + clientIp);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    "Too many login attempts. Please try again later."), 429);
            return;
        }

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("error.invalid_json", "en")), 400);
            return;
        }

        String identifier = req.optString("username", "");
        String password = req.optString("password", "");
        String language = req.optString("language", "en");

        if (adminLogin && configManager.getAdminAuthMode() == AdminAuthMode.OP
                && (opsManager == null || opsManager.getOps().isEmpty())) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("login.system_error", language)));
            return;
        }

        UserRecord user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElse(null);
        if (user == null || user.username() == null || user.username().isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("login.user_not_found", language)));
            return;
        }

        String username = user.username();
        if (adminLogin && !adminAccessManager.hasAnyAdminAccess(username)) {
            plugin.getLogger().warning("[Security] Unauthorized admin login attempt for user: " + username);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("login.not_authorized", language)));
            return;
        }

        boolean passwordValid = false;
        if (authmeService != null && authmeService.isAuthmeEnabled() && authmeService.hasAuthmeUser(username)) {
            String authmePassword = authmeService.getAuthmePassword(username);
            if (authmePassword != null && !authmePassword.isEmpty()) {
                passwordValid = PasswordUtil.verify(password, authmePassword);
            }
        }
        if (!passwordValid && user.passwordHash() != null && !user.passwordHash().isEmpty()) {
            passwordValid = PasswordUtil.verify(password, user.passwordHash());
        }
        if (!passwordValid) {
            plugin.getLogger().warning("[Security] Failed login attempt - User: " + username + ", IP: " + clientIp + ", Reason: Invalid password");
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("login.failed", language)));
            return;
        }

        if (user.passwordHash() != null && PasswordUtil.needsMigration(user.passwordHash())) {
            migratePassword(username, password, user.passwordHash());
        }

        String token = webAuthHelper.generateToken(username);
        JSONObject response = ApiResponseFactory.success(messageResolver.apply("login.success", language));
        response.put("token", token);
        response.put("username", username);
        response.put("isAdmin", adminAccessManager.hasAnyAdminAccess(username));
        WebResponseHelper.sendJson(exchange, response);
    }

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        LoginAttempt attempt = loginAttempts.compute(ip, (key, existing) -> {
            if (existing == null || (now - existing.windowStart) > RATE_LIMIT_WINDOW_MS) {
                LoginAttempt fresh = new LoginAttempt();
                fresh.windowStart = now;
                fresh.count.set(1);
                return fresh;
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return attempt.count.get() > MAX_ATTEMPTS_PER_IP;
    }

    private void migratePassword(String username, String plainPassword, String oldStoredPassword) {
        try {
            String migrationType = PasswordUtil.isPlaintext(oldStoredPassword) ? "plaintext" : "unsalted-sha256";
            boolean success = userRepository.updatePassword(username, plainPassword);
            if (success) {
                plugin.getLogger().info("[VerifyMC] Password migration successful - User: " + username
                        + ", From: " + migrationType + ", To: salted-sha256");
                if (auditService != null) {
                    auditService.recordPasswordMigration(
                            username,
                            "Migrated from " + migrationType + " to salted-sha256"
                    );
                }
            } else {
                plugin.getLogger().warning("[VerifyMC] Password migration failed - User: " + username);
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[VerifyMC] Password migration error - User: " + username, e);
        }
    }
}
