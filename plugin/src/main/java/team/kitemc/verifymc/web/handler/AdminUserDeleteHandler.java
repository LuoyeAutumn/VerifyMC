package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.security.AdminAction;
import team.kitemc.verifymc.util.FoliaCompat;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;

/**
 * Deletes a user from the system and removes them from the whitelist.
 */
public class AdminUserDeleteHandler implements HttpHandler {
    private final PluginContext ctx;

    public AdminUserDeleteHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        // Require admin privileges and get operator username
        String operator = AdminAuthUtil.requireAdmin(exchange, ctx, AdminAction.DELETE);
        if (operator == null) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "en")), 400);
            return;
        }
        String target = req.optString("username", req.optString("uuid", ""));
        String language = req.optString("language", "en");

        if (target.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.missing_user_identifier", language)));
            return;
        }

        String resolvedTarget = ctx.getUsernameRuleService().resolveAdminTarget(target, ctx.getUserDao());
        if (resolvedTarget.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.user_not_found", language)));
            return;
        }

        boolean ok = ctx.getUserDao().deleteUser(resolvedTarget);
        if (ok) {
            FoliaCompat.runTaskGlobal(ctx.getPlugin(), () ->
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist remove " + resolvedTarget));

            if (ctx.getAuthmeService() != null && ctx.getAuthmeService().isAuthmeEnabled()) {
                ctx.getAuthmeService().removeUserFromAuthme(resolvedTarget);
            }

            ctx.getAuditService().recordDeletion(operator, resolvedTarget);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("admin.delete_success", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.delete_failed", language)));
        }
    }
}
