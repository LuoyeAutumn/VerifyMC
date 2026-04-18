package team.kitemc.verifymc.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiFunction;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.WebResponseHelper;
import java.io.IOException;
import team.kitemc.verifymc.user.AdminUserResult;
import team.kitemc.verifymc.user.ResetUserPasswordCommand;
import team.kitemc.verifymc.user.ResetUserPasswordUseCase;

/**
 * Changes a user's password (stored and/or AuthMe).
 */
public class AdminUserPasswordHandler implements HttpHandler {
    private final AuthenticatedRequestContext authContext;
    private final ResetUserPasswordUseCase resetUserPasswordUseCase;
    private final BiFunction<String, String, String> messageResolver;

    public AdminUserPasswordHandler(
            AuthenticatedRequestContext authContext,
            ResetUserPasswordUseCase resetUserPasswordUseCase,
            BiFunction<String, String, String> messageResolver
    ) {
        this.authContext = authContext;
        this.resetUserPasswordUseCase = resetUserPasswordUseCase;
        this.messageResolver = messageResolver;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        // Require admin privileges and get operator username
        String operator = AdminAuthUtil.requireAdmin(exchange, authContext, AdminAction.PASSWORD);
        if (operator == null) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("error.invalid_json", "en")), 400);
            return;
        }
        String target = req.optString("username", req.optString("uuid", ""));
        String password = req.optString("password", "");
        String language = req.optString("language", "en");

        if (target.isBlank() || password.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("admin.missing_user_identifier", language)));
            return;
        }

        AdminUserResult result = resetUserPasswordUseCase.execute(new ResetUserPasswordCommand(operator, target, password));
        JSONObject response = result.success()
                ? ApiResponseFactory.success(messageResolver.apply(result.messageKey(), language))
                : ApiResponseFactory.failure(messageResolver.apply(result.messageKey(), language));
        WebResponseHelper.sendJson(exchange, response);
    }
}

