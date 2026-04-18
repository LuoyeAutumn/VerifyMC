package team.kitemc.verifymc.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiFunction;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.WebResponseHelper;
import java.io.IOException;

/**
 * Verifies an admin token is still valid.
 * Extracted from WebServer.start() — the "/api/admin/verify" context.
 */
public class AdminVerifyHandler implements HttpHandler {
    private final AuthenticatedRequestContext authContext;
    private final BiFunction<String, String, String> messageResolver;

    public AdminVerifyHandler(
            AuthenticatedRequestContext authContext,
            BiFunction<String, String, String> messageResolver
    ) {
        this.authContext = authContext;
        this.messageResolver = messageResolver;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("error.invalid_json", "en")), 400);
            return;
        }
        String token = req.optString("token", "");
        String language = req.optString("language", "en");

        if (authContext.getWebAuthHelper().isValidToken(token)) {
            String username = authContext.getWebAuthHelper().getUsername(token);
            if (username == null || !authContext.getAdminAccessManager().hasAnyAdminAccess(username)) {
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        messageResolver.apply("login.not_authorized", language)), 403);
                return;
            }
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    messageResolver.apply("login.token_valid", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("login.token_invalid", language)));
        }
    }
}

