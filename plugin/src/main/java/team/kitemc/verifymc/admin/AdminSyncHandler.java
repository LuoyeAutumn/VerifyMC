package team.kitemc.verifymc.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.logging.Level;
import org.json.JSONException;
import org.json.JSONObject;
import org.bukkit.plugin.Plugin;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.WebResponseHelper;

public class AdminSyncHandler implements HttpHandler {
    private final AuthenticatedRequestContext authContext;
    private final AdminAuthmeSyncPort authmeSyncPort;
    private final Plugin plugin;
    private final BiFunction<String, String, String> messageResolver;

    public AdminSyncHandler(
            AuthenticatedRequestContext authContext,
            AdminAuthmeSyncPort authmeSyncPort,
            Plugin plugin,
            BiFunction<String, String, String> messageResolver
    ) {
        this.authContext = authContext;
        this.authmeSyncPort = authmeSyncPort;
        this.plugin = plugin;
        this.messageResolver = messageResolver;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        // Require admin privileges
        if (AdminAuthUtil.requireAdmin(exchange, authContext, AdminAction.SYNC) == null) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("error.invalid_json", "en")), 400);
            return;
        }
        String language = req.optString("language", "en");

        if (!authmeSyncPort.isAvailable()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("authme.service_unavailable", language)), 500);
            return;
        }

        if (!authmeSyncPort.isEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("authme.not_enabled", language)), 400);
            return;
        }

        try {
            authmeSyncPort.syncApprovedUsers();
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    messageResolver.apply("authme.sync_success", language)));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "AuthMe sync failed", e);
            String errorMsg = messageResolver.apply("authme.sync_failed", language).replace("{error}", e.getMessage());
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(errorMsg), 500);
        }
    }
}

