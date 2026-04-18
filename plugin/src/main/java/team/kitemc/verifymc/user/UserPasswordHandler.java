package team.kitemc.verifymc.user;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.admin.AdminAuthUtil;
import team.kitemc.verifymc.admin.AuthenticatedRequestContext;
import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.platform.WebResponseHelper;
import team.kitemc.verifymc.registration.VerifyCodePurpose;
import team.kitemc.verifymc.registration.VerifyCodeService;
import team.kitemc.verifymc.shared.PasswordUtil;

public class UserPasswordHandler implements HttpHandler {
    private final AuthenticatedRequestContext authContext;
    private final ConfigManager configManager;
    private final UserPasswordPolicy userPasswordPolicy;
    private final UserRepository userRepository;
    private final UserAccessSyncPort userAccessSyncPort;
    private final AuditService auditService;
    private final VerifyCodeService verifyCodeService;

    public UserPasswordHandler(
            AuthenticatedRequestContext authContext,
            ConfigManager configManager,
            UserPasswordPolicy userPasswordPolicy,
            UserRepository userRepository,
            UserAccessSyncPort userAccessSyncPort,
            AuditService auditService,
            VerifyCodeService verifyCodeService
    ) {
        this.authContext = authContext;
        this.configManager = configManager;
        this.userPasswordPolicy = userPasswordPolicy;
        this.userRepository = userRepository;
        this.userAccessSyncPort = userAccessSyncPort;
        this.auditService = auditService;
        this.verifyCodeService = verifyCodeService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        String username = AdminAuthUtil.getAuthenticatedUser(exchange, authContext);
        if (username == null) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("error.invalid_json", "en")), 400);
            return;
        }

        String language = req.optString("language", "en");
        String currentPassword = req.optString("currentPassword", "");
        String newPassword = req.optString("newPassword", "");
        String emailCode = req.optString("code", "");

        if (newPassword.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("user.new_password_required", language)));
            return;
        }

        String passwordRegex = userPasswordPolicy.getPasswordRegex();
        if (!newPassword.matches(passwordRegex)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("admin.invalid_password", language).replace("{regex}", passwordRegex)));
            return;
        }

        UserRecord user = userRepository.findByUsernameConfigured(username).orElse(null);
        if (user == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("error.user_not_found", language)));
            return;
        }

        String storedPassword = user.passwordHash();
        java.util.Set<PasswordResetMethod> resetMethods = PasswordResetMethod.sanitize(configManager.getUserPasswordResetMethods());
        boolean currentPasswordVerified = false;
        boolean emailCodeVerified = false;

        if (resetMethods.contains(PasswordResetMethod.CURRENT_PASSWORD)
                && storedPassword != null
                && !storedPassword.isBlank()
                && !currentPassword.isBlank()) {
            currentPasswordVerified = PasswordUtil.verify(currentPassword, storedPassword);
        }

        if (resetMethods.contains(PasswordResetMethod.EMAIL_CODE)
                && user.email() != null
                && !user.email().isBlank()
                && !emailCode.isBlank()) {
            emailCodeVerified = verifyCodeService.checkCode(VerifyCodePurpose.CHANGE_PASSWORD, user.email(), emailCode);
        }

        if (!currentPasswordVerified && !emailCodeVerified) {
            String failureKey = resolveVerificationFailureKey(resetMethods, user, storedPassword, currentPassword, emailCode);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage(failureKey, language)));
            return;
        }

        boolean updated = userRepository.updatePassword(username, newPassword);

        if (updated) {
            userAccessSyncPort.syncPasswordChange(username, newPassword);

            auditService.recordPasswordChange(username, username, "User changed own password");
             
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    authContext.getMessage("admin.password_change_success", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("admin.password_change_failed", language)));
        }
    }

    private String resolveVerificationFailureKey(
            java.util.Set<PasswordResetMethod> resetMethods,
            UserRecord user,
            String storedPassword,
            String currentPassword,
            String emailCode
    ) {
        if (resetMethods.size() > 1) {
            return "user.password_reset_verification_failed";
        }
        if (resetMethods.contains(PasswordResetMethod.EMAIL_CODE)) {
            if (user.email() == null || user.email().isBlank()) {
                return "user.email_not_bound";
            }
            return emailCode == null || emailCode.isBlank()
                    ? "verify.code_required"
                    : "verify.wrong_code";
        }
        if (storedPassword == null || storedPassword.isBlank()) {
            return "user.password_not_set";
        }
        return currentPassword == null || currentPassword.isBlank()
                ? "user.current_password_required"
                : "user.current_password_incorrect";
    }
}

