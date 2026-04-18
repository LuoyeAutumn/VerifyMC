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
import team.kitemc.verifymc.platform.WebResponseHelper;
import team.kitemc.verifymc.shared.PasswordUtil;

public class UserPasswordHandler implements HttpHandler {
    private final AuthenticatedRequestContext authContext;
    private final UserPasswordPolicy userPasswordPolicy;
    private final UserRepository userRepository;
    private final UserAccessSyncPort userAccessSyncPort;
    private final AuditService auditService;

    public UserPasswordHandler(
            AuthenticatedRequestContext authContext,
            UserPasswordPolicy userPasswordPolicy,
            UserRepository userRepository,
            UserAccessSyncPort userAccessSyncPort,
            AuditService auditService
    ) {
        this.authContext = authContext;
        this.userPasswordPolicy = userPasswordPolicy;
        this.userRepository = userRepository;
        this.userAccessSyncPort = userAccessSyncPort;
        this.auditService = auditService;
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

        if (currentPassword.isBlank() || newPassword.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("admin.password_required", language)));
            return;
        }

        String passwordRegex = userPasswordPolicy.getPasswordRegex();
        if (!newPassword.matches(passwordRegex)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("admin.invalid_password", language).replace("{regex}", passwordRegex)));
            return;
        }

        UserRecord user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("error.user_not_found", language)));
            return;
        }

        String storedPassword = user.passwordHash();
        if (storedPassword == null || storedPassword.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("user.password_not_set", language)));
            return;
        }

        if (!PasswordUtil.verify(currentPassword, storedPassword)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("user.current_password_incorrect", language)));
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
}

