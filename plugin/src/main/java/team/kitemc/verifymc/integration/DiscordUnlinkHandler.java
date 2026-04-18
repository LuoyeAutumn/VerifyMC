package team.kitemc.verifymc.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.function.BiFunction;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.admin.AdminAction;
import team.kitemc.verifymc.admin.AdminAuthUtil;
import team.kitemc.verifymc.admin.AuthenticatedRequestContext;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.WebResponseHelper;

/**
 * Unlinks a Discord account from a user.
 * Requires authentication: only the user themselves or an admin can unlink.
 */
public class DiscordUnlinkHandler implements HttpHandler {
    private final AuthenticatedRequestContext authContext;
    private final DiscordService discordService;
    private final BiFunction<String, String, String> messageResolver;

    public DiscordUnlinkHandler(
            AuthenticatedRequestContext authContext,
            DiscordService discordService,
            BiFunction<String, String, String> messageResolver
    ) {
        this.authContext = authContext;
        this.discordService = discordService;
        this.messageResolver = messageResolver;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        // Step 1: Authenticate the request
        String authenticatedUser = AdminAuthUtil.getAuthenticatedUser(exchange, authContext);
        if (authenticatedUser == null) {
            return; // Response already sent by getAuthenticatedUser
        }

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("error.invalid_json", "en")), 400);
            return;
        }
        String targetUsername = req.optString("username", "");
        String language = req.optString("language", "en");

        if (targetUsername.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("admin.missing_user_identifier", language)));
            return;
        }

        // Step 2: Check authorization - user must be the target user or an admin
        String resolvedTargetUsername = discordService.resolveStoredUsername(targetUsername);
        boolean isSelf = authenticatedUser.equals(resolvedTargetUsername);

        if (!isSelf && !authContext.getAdminAccessManager().canAccess(authenticatedUser, AdminAction.UNLINK)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("admin.forbidden", language)), 403);
            return;
        }

        boolean ok = discordService.unlinkUser(resolvedTargetUsername);
        if (ok) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    messageResolver.apply("discord.link_success", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("discord.link_failed", language)));
        }
    }
}

