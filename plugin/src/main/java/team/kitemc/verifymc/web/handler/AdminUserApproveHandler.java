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
 * Approves a pending user — updates status to "approved" and whitelists in-game.
 * Extracted from WebServer.start() — the "/api/admin/user/approve" context.
 */
public class AdminUserApproveHandler implements HttpHandler {
    private final PluginContext ctx;

    public AdminUserApproveHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        // Require admin privileges and get operator username
        String operator = AdminAuthUtil.requireAdmin(exchange, ctx, AdminAction.APPROVE);
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

        boolean ok = ctx.getUserDao().updateUserStatus(resolvedTarget, "approved", operator);
        if (ok) {
            FoliaCompat.runTaskGlobal(ctx.getPlugin(), () ->
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist add " + resolvedTarget));

            if (ctx.getAuthmeService() != null && ctx.getAuthmeService().isAuthmeEnabled()) {
                ctx.getAuthmeService().syncApprovedUserToAuthme(resolvedTarget);
            }

            var user = ctx.getUserDao().getUserByUsername(resolvedTarget);
            if (user != null) {
                String email = (String) user.get("email");
                if (email != null && !email.isEmpty()) {
                    ctx.getMailService().sendReviewResult(email, resolvedTarget, true,
                            "", ctx.getConfigManager().getLanguage());
                }
            }

            ctx.getAuditService().recordApproval(operator, resolvedTarget);

            if (ctx.getWsServer() != null) {
                ctx.getWsServer().broadcastMessage(new JSONObject()
                        .put("type", "user_approved")
                        .put("username", resolvedTarget).toString());
            }

            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("review.approve_success", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("review.failed", language)));
        }
    }
}
