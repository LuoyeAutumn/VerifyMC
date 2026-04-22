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
 * Bans a user — updates status and removes from whitelist.
 */
public class AdminUserBanHandler implements HttpHandler {
    private final PluginContext ctx;

    public AdminUserBanHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        // Require admin privileges and get operator username
        String operator = AdminAuthUtil.requireAdmin(exchange, ctx, AdminAction.BAN);
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
        String reason = req.optString("reason", "");
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

        boolean ok = ctx.getUserDao().banUser(resolvedTarget);
        if (ok) {
            FoliaCompat.runTaskGlobal(ctx.getPlugin(), () ->
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist remove " + resolvedTarget));

            if (ctx.getAuthmeService() != null && ctx.getAuthmeService().isAuthmeEnabled()) {
                ctx.getAuthmeService().removeUserFromAuthme(resolvedTarget);
            }

            ctx.getAuditService().recordBan(operator, resolvedTarget, reason);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("admin.ban_success", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.ban_failed", language)));
        }
    }
}
