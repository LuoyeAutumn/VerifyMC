package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.registration.VerifyCodePurpose;
import team.kitemc.verifymc.util.EmailAddressUtil;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

public class ForgotPasswordResetHandler implements HttpHandler {
    private final PluginContext ctx;

    public ForgotPasswordResetHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "en")), 400);
            return;
        }
        String email = EmailAddressUtil.normalize(req.optString("email", ""));
        String code = req.optString("code", "");
        String newPassword = req.optString("password", "");
        String language = req.optString("language", "en");

        if (!ctx.getConfigManager().isForgotPasswordEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("forgot_password.disabled", language)));
            return;
        }

        if (!EmailAddressUtil.isValid(email)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("email.invalid_format", language)));
            return;
        }

        if (code.isBlank() || code.length() != 6) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("email.invalid_code", language)));
            return;
        }

        String passwordRegex = ctx.getConfigManager().getAuthmePasswordRegex();
        if (!newPassword.matches(passwordRegex)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("register.password_invalid", language)));
            return;
        }

        if (!ctx.getVerifyCodeService().checkCode(VerifyCodePurpose.FORGOT_PASSWORD, email, code)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("email.invalid_code", language)));
            return;
        }

        List<Map<String, Object>> users = ctx.getUserDao().findAllByEmail(email);
        if (users.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("forgot_password.email_not_found", language)));
            return;
        }

        int successCount = 0;
        for (Map<String, Object> user : users) {
            String username = (String) user.get("username");
            if (ctx.getUserDao().updatePassword(username, newPassword)) {
                successCount++;
                if (ctx.getAuthmeService() != null && ctx.getAuthmeService().isAuthmeEnabled()) {
                    ctx.getAuthmeService().syncUserPasswordToAuthme(username, newPassword);
                }
                ctx.getAuditService().recordPasswordChange("system", username, "Forgot password via email verification");
            }
        }

        if (successCount > 0) {
            JSONObject response = ApiResponseFactory.success(ctx.getMessage("forgot_password.reset_success", language));
            response.put("resetCount", successCount);
            WebResponseHelper.sendJson(exchange, response);
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.unknown", language)));
        }
    }
}
