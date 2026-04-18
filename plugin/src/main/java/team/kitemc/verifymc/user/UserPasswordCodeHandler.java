package team.kitemc.verifymc.user;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.admin.AdminAuthUtil;
import team.kitemc.verifymc.admin.AuthenticatedRequestContext;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.platform.WebResponseHelper;

public class UserPasswordCodeHandler implements HttpHandler {
    private final AuthenticatedRequestContext authContext;
    private final ConfigManager configManager;
    private final SendUserPasswordCodeUseCase sendUserPasswordCodeUseCase;

    public UserPasswordCodeHandler(
            AuthenticatedRequestContext authContext,
            ConfigManager configManager,
            SendUserPasswordCodeUseCase sendUserPasswordCodeUseCase
    ) {
        this.authContext = authContext;
        this.configManager = configManager;
        this.sendUserPasswordCodeUseCase = sendUserPasswordCodeUseCase;
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
        if (!PasswordResetMethod.sanitize(configManager.getUserPasswordResetMethods()).contains(PasswordResetMethod.EMAIL_CODE)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    authContext.getMessage("user.email_code_not_enabled", language)));
            return;
        }

        PasswordCodeSendResult result = sendUserPasswordCodeUseCase.execute(username, language);
        String message = authContext.getMessage(result.messageKey(), language);
        if ("email.rate_limited".equals(result.messageKey())) {
            message = message.replace("{seconds}", String.valueOf(result.remainingSeconds()));
        }

        JSONObject response = result.success()
                ? ApiResponseFactory.success(message)
                : ApiResponseFactory.failure(message);
        if (result.remainingSeconds() > 0) {
            response.put("remainingSeconds", result.remainingSeconds());
        }
        WebResponseHelper.sendJson(exchange, response);
    }
}
