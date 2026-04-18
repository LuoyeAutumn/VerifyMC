package team.kitemc.verifymc.user;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiFunction;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.admin.AdminAuthUtil;
import team.kitemc.verifymc.admin.AuthenticatedRequestContext;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.WebResponseHelper;
import java.io.IOException;

public class UserUpdateHandler implements HttpHandler {
    private final AuthenticatedRequestContext authContext;
    private final UpdateEmailUseCase updateEmailUseCase;
    private final BiFunction<String, String, String> messageResolver;

    public UserUpdateHandler(
            AuthenticatedRequestContext authContext,
            UpdateEmailUseCase updateEmailUseCase,
            BiFunction<String, String, String> messageResolver
    ) {
        this.authContext = authContext;
        this.updateEmailUseCase = updateEmailUseCase;
        this.messageResolver = messageResolver;
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
                    messageResolver.apply("error.invalid_json", "en")), 400);
            return;
        }

        String language = req.optString("language", "en");
        String newEmail = team.kitemc.verifymc.shared.EmailAddressUtil.normalize(req.optString("email", ""));
        UpdateEmailResult result = updateEmailUseCase.execute(new UpdateEmailCommand(username, newEmail));
        String message = messageResolver.apply(result.messageKey(), language);
        JSONObject response = result.success()
                ? ApiResponseFactory.success(message)
                : ApiResponseFactory.failure(message);
        WebResponseHelper.sendJson(exchange, response);
    }
}

