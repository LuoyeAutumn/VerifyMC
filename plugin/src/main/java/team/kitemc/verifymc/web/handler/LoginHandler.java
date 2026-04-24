package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.OpsManager;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.UserDao;
import team.kitemc.verifymc.registration.VerifyCodePurpose;
import team.kitemc.verifymc.service.AuthmeService;
import team.kitemc.verifymc.service.VerifyCodeService;
import team.kitemc.verifymc.security.AdminAuthMode;
import team.kitemc.verifymc.util.PasswordUtil;
import team.kitemc.verifymc.util.PhoneUtil;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebAuthHelper;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LoginHandler implements HttpHandler {
    private static final int MAX_ATTEMPTS_PER_IP = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;
    private static final long TEMP_TOKEN_EXPIRY_MS = 300_000L;

    private final PluginContext ctx;
    private final boolean isAdminLogin;
    private final ConcurrentHashMap<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AccountSelectionToken> accountSelectionTokens = new ConcurrentHashMap<>();

    private static class LoginAttempt {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
    }

    private static class AccountSelectionToken {
        final List<String> usernames;
        final long expiryTime;

        AccountSelectionToken(List<String> usernames, long expiryTime) {
            this.usernames = usernames;
            this.expiryTime = expiryTime;
        }
    }

    public LoginHandler(PluginContext ctx) {
        this(ctx, false);
    }

    public LoginHandler(PluginContext ctx, boolean isAdminLogin) {
        this.ctx = ctx;
        this.isAdminLogin = isAdminLogin;
    }

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        LoginAttempt attempt = loginAttempts.compute(ip, (k, v) -> {
            if (v == null || (now - v.windowStart) > RATE_LIMIT_WINDOW_MS) {
                LoginAttempt fresh = new LoginAttempt();
                fresh.windowStart = now;
                fresh.count.set(1);
                return fresh;
            }
            v.count.incrementAndGet();
            return v;
        });
        return attempt.count.get() > MAX_ATTEMPTS_PER_IP;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (isRateLimited(clientIp)) {
            ctx.getPlugin().getLogger().warning("[Security] Login rate limit exceeded for IP: " + clientIp);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    "Too many login attempts. Please try again later."), 429);
            return;
        }

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "en")), 400);
            return;
        }

        String language = req.optString("language", "en");
        String tempToken = req.optString("tempToken", "");
        String selectedUsername = req.optString("selectedUsername", "");

        if (!tempToken.isEmpty() && !selectedUsername.isEmpty()) {
            handleAccountSelection(exchange, tempToken, selectedUsername, language, clientIp);
            return;
        }

        String loginMethod = req.optString("loginMethod", "username").toLowerCase();
        String identifier = req.optString("username", "");
        String password = req.optString("password", "");
        String verifyMethod = req.optString("verifyMethod", "password").toLowerCase();
        String code = req.optString("code", "");
        String countryCode = req.optString("countryCode", "+86");

        if (!ctx.getConfigManager().isLoginMethodAllowed(loginMethod)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.method_not_allowed", language)), 400);
            return;
        }

        if ("phone".equals(loginMethod) && !ctx.getConfigManager().isSmsEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.phone_not_enabled", language)), 400);
            return;
        }

        OpsManager opsManager = ctx.getOpsManager();
        if (isAdminLogin && ctx.getConfigManager().getAdminAuthMode() == AdminAuthMode.OP
                && (opsManager == null || opsManager.getOps().isEmpty())) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.system_error", language)));
            return;
        }

        boolean useCodeLogin = "code".equals(verifyMethod) && 
                ("email".equals(loginMethod) || "phone".equals(loginMethod));

        switch (loginMethod) {
            case "email":
                if (useCodeLogin) {
                    handleEmailCodeLogin(exchange, identifier, code, language, clientIp);
                } else {
                    handleEmailLogin(exchange, identifier, password, language, clientIp);
                }
                break;
            case "phone":
                if (useCodeLogin) {
                    handlePhoneCodeLogin(exchange, identifier, countryCode, code, language, clientIp);
                } else {
                    handlePhoneLogin(exchange, identifier, password, language, clientIp);
                }
                break;
            case "username":
            default:
                handleUsernameLogin(exchange, identifier, password, language, clientIp);
                break;
        }
    }

    private void handleUsernameLogin(HttpExchange exchange, String username, String password,
                                     String language, String clientIp) throws IOException {
        UserDao userDao = ctx.getUserDao();
        Map<String, Object> user = userDao.getUserByUsernameConfigured(username);

        if (user == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.user_not_found", language)));
            return;
        }

        String actualUsername = (String) user.get("username");
        if (actualUsername == null || actualUsername.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.user_not_found", language)));
            return;
        }

        if (!verifyPassword(user, password)) {
            ctx.getPlugin().getLogger().warning("[Security] Failed login attempt - User: " + actualUsername + ", IP: " + clientIp + ", Reason: Invalid password");
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.failed", language)));
            return;
        }

        completeLogin(exchange, user, password, language);
    }

    private void handleEmailLogin(HttpExchange exchange, String email, String password,
                                  String language, String clientIp) throws IOException {
        UserDao userDao = ctx.getUserDao();
        List<Map<String, Object>> users = userDao.findAllByEmail(email);

        if (users.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.user_not_found", language)));
            return;
        }

        List<Map<String, Object>> validUsers = new ArrayList<>();
        for (Map<String, Object> user : users) {
            if (verifyPassword(user, password)) {
                validUsers.add(user);
            }
        }

        if (validUsers.isEmpty()) {
            ctx.getPlugin().getLogger().warning("[Security] Failed login attempt - Email: " + email + ", IP: " + clientIp + ", Reason: Invalid password");
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.failed", language)));
            return;
        }

        if (validUsers.size() == 1) {
            completeLogin(exchange, validUsers.get(0), password, language);
            return;
        }

        requireAccountSelection(exchange, validUsers, language);
    }

    private void handleEmailCodeLogin(HttpExchange exchange, String email, String code,
                                       String language, String clientIp) throws IOException {
        UserDao userDao = ctx.getUserDao();
        List<Map<String, Object>> users = userDao.findAllByEmail(email);

        if (users.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.email_not_found", language)));
            return;
        }

        if (code == null || code.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("verification.code_required", language)));
            return;
        }

        VerifyCodeService verifyCodeService = ctx.getVerifyCodeService();
        boolean codeValid = verifyCodeService.checkCode(VerifyCodePurpose.EMAIL_LOGIN, email, code);

        if (!codeValid) {
            ctx.getPlugin().getLogger().warning("[Security] Failed login attempt - Email: " + email + ", IP: " + clientIp + ", Reason: Invalid code");
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("verification.code_invalid", language)));
            return;
        }

        if (users.size() == 1) {
            completeLogin(exchange, users.get(0), null, language);
            return;
        }

        requireAccountSelection(exchange, users, language);
    }

    private void handlePhoneCodeLogin(HttpExchange exchange, String phone, String countryCode, 
                                       String code, String language, String clientIp) throws IOException {
        String normalizedPhone = PhoneUtil.normalizePhoneNumber(phone);
        String fullPhone = PhoneUtil.buildFullPhoneNumber(countryCode, normalizedPhone);
        
        UserDao userDao = ctx.getUserDao();
        List<Map<String, Object>> users = userDao.findAllByPhone(fullPhone);

        if (users.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.phone_not_found", language)));
            return;
        }

        if (code == null || code.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("verification.code_required", language)));
            return;
        }

        VerifyCodeService verifyCodeService = ctx.getVerifyCodeService();
        boolean codeValid = verifyCodeService.checkSmsCode(normalizedPhone, countryCode, code, VerifyCodePurpose.SMS_LOGIN);

        if (!codeValid) {
            ctx.getPlugin().getLogger().warning("[Security] Failed login attempt - Phone: " + fullPhone + ", IP: " + clientIp + ", Reason: Invalid code");
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("verification.code_invalid", language)));
            return;
        }

        if (users.size() == 1) {
            completeLogin(exchange, users.get(0), null, language);
            return;
        }

        requireAccountSelection(exchange, users, language);
    }

    private void handlePhoneLogin(HttpExchange exchange, String phone, String password,
                                  String language, String clientIp) throws IOException {
        UserDao userDao = ctx.getUserDao();
        List<Map<String, Object>> users = userDao.findAllByPhone(phone);

        if (users.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.user_not_found", language)));
            return;
        }

        List<Map<String, Object>> validUsers = new ArrayList<>();
        for (Map<String, Object> user : users) {
            if (verifyPassword(user, password)) {
                validUsers.add(user);
            }
        }

        if (validUsers.isEmpty()) {
            ctx.getPlugin().getLogger().warning("[Security] Failed login attempt - Phone: " + phone + ", IP: " + clientIp + ", Reason: Invalid password");
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.failed", language)));
            return;
        }

        if (validUsers.size() == 1) {
            completeLogin(exchange, validUsers.get(0), password, language);
            return;
        }

        requireAccountSelection(exchange, validUsers, language);
    }

    private void handleAccountSelection(HttpExchange exchange, String tempToken, String selectedUsername,
                                        String language, String clientIp) throws IOException {
        AccountSelectionToken tokenInfo = accountSelectionTokens.get(tempToken);

        if (tokenInfo == null || System.currentTimeMillis() > tokenInfo.expiryTime) {
            if (tokenInfo != null) {
                accountSelectionTokens.remove(tempToken);
            }
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.token_expired", language)), 401);
            return;
        }

        if (!tokenInfo.usernames.contains(selectedUsername)) {
            ctx.getPlugin().getLogger().warning("[Security] Invalid account selection - Username: " + selectedUsername + ", IP: " + clientIp);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.invalid_account_selection", language)), 400);
            return;
        }

        UserDao userDao = ctx.getUserDao();
        Map<String, Object> user = userDao.getUserByUsernameConfigured(selectedUsername);

        if (user == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.user_not_found", language)));
            return;
        }

        accountSelectionTokens.remove(tempToken);

        completeLogin(exchange, user, null, language);
    }

    private boolean verifyPassword(Map<String, Object> user, String password) {
        String actualUsername = (String) user.get("username");
        String storedPassword = (String) user.get("password");
        AuthmeService authmeService = ctx.getAuthmeService();
        boolean passwordValid = false;

        if (authmeService != null && authmeService.isAuthmeEnabled() && authmeService.hasAuthmeUser(actualUsername)) {
            String authmePassword = authmeService.getAuthmePassword(actualUsername);
            if (authmePassword != null && !authmePassword.isEmpty()) {
                passwordValid = PasswordUtil.verify(password, authmePassword);
            }
        }

        if (!passwordValid && storedPassword != null && !storedPassword.isEmpty()) {
            passwordValid = PasswordUtil.verify(password, storedPassword);
        }

        return passwordValid;
    }

    private void requireAccountSelection(HttpExchange exchange, List<Map<String, Object>> users,
                                         String language) throws IOException {
        List<String> usernames = new ArrayList<>();
        JSONArray accountsArray = new JSONArray();

        for (Map<String, Object> user : users) {
            String username = (String) user.get("username");
            if (username != null && !username.isEmpty()) {
                usernames.add(username);

                JSONObject accountInfo = new JSONObject();
                accountInfo.put("username", username);
                accountInfo.put("email", user.get("email"));
                accountInfo.put("status", user.get("status"));
                accountsArray.put(accountInfo);
            }
        }

        WebAuthHelper webAuthHelper = ctx.getWebAuthHelper();
        String tempToken = webAuthHelper.generateToken("__account_selection__");

        accountSelectionTokens.put(tempToken, new AccountSelectionToken(
                usernames, System.currentTimeMillis() + TEMP_TOKEN_EXPIRY_MS));

        JSONObject resp = ApiResponseFactory.success(ctx.getMessage("login.account_selection_required", language));
        resp.put("requireAccountSelection", true);
        resp.put("tempToken", tempToken);
        resp.put("accounts", accountsArray);

        WebResponseHelper.sendJson(exchange, resp);
    }

    private void completeLogin(HttpExchange exchange, Map<String, Object> user, String password,
                               String language) throws IOException {
        String actualUsername = (String) user.get("username");

        if (isAdminLogin) {
            if (!ctx.getAdminAccessManager().hasAnyAdminAccess(actualUsername)) {
                ctx.getPlugin().getLogger().warning("[Security] Unauthorized admin login attempt for user: " + actualUsername);
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        ctx.getMessage("login.not_authorized", language)));
                return;
            }
        }

        if (password != null) {
            String storedPassword = (String) user.get("password");
            if (storedPassword != null && PasswordUtil.needsMigration(storedPassword)) {
                migratePassword(actualUsername, password, storedPassword);
            }
        }

        WebAuthHelper webAuthHelper = ctx.getWebAuthHelper();
        String token = webAuthHelper.generateToken(actualUsername);
        boolean isAdmin = ctx.getAdminAccessManager().hasAnyAdminAccess(actualUsername);
        JSONObject resp = ApiResponseFactory.success(ctx.getMessage("login.success", language));
        resp.put("token", token);
        resp.put("username", actualUsername);
        resp.put("isAdmin", isAdmin);
        WebResponseHelper.sendJson(exchange, resp);
    }

    private void migratePassword(String username, String plainPassword, String oldStoredPassword) {
        try {
            String migrationType = PasswordUtil.isPlaintext(oldStoredPassword) ? "plaintext" : "unsalted-sha256";
            boolean success = ctx.getUserDao().updateUserPassword(username, plainPassword);

            if (success) {
                ctx.getPlugin().getLogger().info("[VerifyMC] Password migration successful - User: " + username +
                        ", From: " + migrationType + ", To: salted-sha256");

                if (ctx.getAuditService() != null) {
                    ctx.getAuditService().recordPasswordMigration(
                            username,
                            "Migrated from " + migrationType + " to salted-sha256"
                    );
                }
            } else {
                ctx.getPlugin().getLogger().warning("[VerifyMC] Password migration failed - User: " + username);
            }
        } catch (Exception e) {
            ctx.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
                    "[VerifyMC] Password migration error - User: " + username, e);
        }
    }
}
