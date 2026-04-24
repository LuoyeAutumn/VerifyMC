package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.security.AdminAction;
import team.kitemc.verifymc.util.FoliaCompat;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminUserHandler implements HttpHandler {
    private final PluginContext ctx;

    public AdminUserHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String action = extractAction(path);

        if (action == null) {
            handleList(exchange);
        } else {
            handleAction(exchange, action);
        }
    }

    private String extractAction(String path) {
        String prefix = "/api/admin/user/";
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return null;
    }

    private void handleList(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        if (AdminAuthUtil.requireAdmin(exchange, ctx, AdminAction.LIST) == null) return;

        String query = exchange.getRequestURI().getQuery();
        int page = 1, size = 20;
        String search = null, status = null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length != 2) continue;
                switch (kv[0]) {
                    case "page" -> { try { page = Integer.parseInt(kv[1]); } catch (NumberFormatException ignored) {} }
                    case "size" -> { try { size = Integer.parseInt(kv[1]); } catch (NumberFormatException ignored) {} }
                    case "search" -> search = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    case "status" -> status = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        }

        List<Map<String, Object>> users = ctx.getUserDao().getUsers(page, size, search, status);
        int total = ctx.getUserDao().getTotalUsers(search, status);
        int totalPages = (int) Math.ceil((double) total / size);

        JSONArray usersArray = new JSONArray();
        for (Map<String, Object> user : users) {
            Map<String, Object> safeUser = new HashMap<>(user);
            safeUser.remove("password");
            usersArray.put(new JSONObject(safeUser));
        }

        JSONObject pagination = new JSONObject();
        pagination.put("currentPage", page);
        pagination.put("pageSize", size);
        pagination.put("totalCount", total);
        pagination.put("totalPages", totalPages);
        pagination.put("hasNext", page < totalPages);
        pagination.put("hasPrev", page > 1);

        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("users", usersArray);
        resp.put("pagination", pagination);
        WebResponseHelper.sendJson(exchange, resp);
    }

    private void handleAction(HttpExchange exchange, String action) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        AdminAction adminAction = mapToAdminAction(action);
        if (adminAction == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure("Unknown action: " + action), 400);
            return;
        }

        String operator = AdminAuthUtil.requireAdmin(exchange, ctx, adminAction);
        if (operator == null) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "en")), 400);
            return;
        }

        String language = req.optString("language", "en");

        switch (action) {
            case "approve" -> handleApprove(exchange, req, operator, language);
            case "reject" -> handleReject(exchange, req, operator, language);
            case "ban" -> handleBan(exchange, req, operator, language);
            case "unban" -> handleUnban(exchange, req, operator, language);
            case "delete" -> handleDelete(exchange, req, operator, language);
            case "password" -> handlePassword(exchange, req, operator, language);
            default -> WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure("Unknown action: " + action), 400);
        }
    }

    private AdminAction mapToAdminAction(String action) {
        return switch (action) {
            case "approve" -> AdminAction.APPROVE;
            case "reject" -> AdminAction.REJECT;
            case "ban" -> AdminAction.BAN;
            case "unban" -> AdminAction.UNBAN;
            case "delete" -> AdminAction.DELETE;
            case "password" -> AdminAction.PASSWORD;
            default -> null;
        };
    }

    private String resolveTarget(JSONObject req, String language, HttpExchange exchange) throws IOException {
        String target = req.optString("username", req.optString("uuid", ""));
        if (target.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.missing_user_identifier", language)));
            return null;
        }

        String resolvedTarget = ctx.getUsernameRuleService().resolveAdminTarget(target, ctx.getUserDao());
        if (resolvedTarget.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.user_not_found", language)));
            return null;
        }

        return resolvedTarget;
    }

    private void handleApprove(HttpExchange exchange, JSONObject req, String operator, String language) throws IOException {
        String resolvedTarget = resolveTarget(req, language, exchange);
        if (resolvedTarget == null) return;

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

    private void handleReject(HttpExchange exchange, JSONObject req, String operator, String language) throws IOException {
        String resolvedTarget = resolveTarget(req, language, exchange);
        if (resolvedTarget == null) return;

        String reason = req.optString("reason", "");

        boolean ok = ctx.getUserDao().updateUserStatus(resolvedTarget, "rejected", operator);
        if (ok) {
            var user = ctx.getUserDao().getUserByUsername(resolvedTarget);
            if (user != null) {
                String email = (String) user.get("email");
                if (email != null && !email.isEmpty()) {
                    ctx.getMailService().sendReviewResult(email, resolvedTarget, false,
                            reason, ctx.getConfigManager().getLanguage());
                }
            }

            ctx.getAuditService().recordRejection(operator, resolvedTarget, reason);

            if (ctx.getWsServer() != null) {
                ctx.getWsServer().broadcastMessage(new JSONObject()
                        .put("type", "user_rejected")
                        .put("username", resolvedTarget).toString());
            }

            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("review.reject_success", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("review.failed", language)));
        }
    }

    private void handleBan(HttpExchange exchange, JSONObject req, String operator, String language) throws IOException {
        String resolvedTarget = resolveTarget(req, language, exchange);
        if (resolvedTarget == null) return;

        String reason = req.optString("reason", "");

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

    private void handleUnban(HttpExchange exchange, JSONObject req, String operator, String language) throws IOException {
        String resolvedTarget = resolveTarget(req, language, exchange);
        if (resolvedTarget == null) return;

        boolean ok = ctx.getUserDao().unbanUser(resolvedTarget);
        if (ok) {
            FoliaCompat.runTaskGlobal(ctx.getPlugin(), () ->
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist add " + resolvedTarget));

            if (ctx.getAuthmeService() != null && ctx.getAuthmeService().isAuthmeEnabled()) {
                ctx.getAuthmeService().syncApprovedUserToAuthme(resolvedTarget);
            }

            ctx.getAuditService().recordUnban(operator, resolvedTarget);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("admin.unban_success", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.unban_failed", language)));
        }
    }

    private void handleDelete(HttpExchange exchange, JSONObject req, String operator, String language) throws IOException {
        String resolvedTarget = resolveTarget(req, language, exchange);
        if (resolvedTarget == null) return;

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

    private void handlePassword(HttpExchange exchange, JSONObject req, String operator, String language) throws IOException {
        String resolvedTarget = resolveTarget(req, language, exchange);
        if (resolvedTarget == null) return;

        String password = req.optString("password", "");
        if (password.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.password_required", language)));
            return;
        }

        boolean ok = ctx.getUserDao().updatePassword(resolvedTarget, password);

        if (ok && ctx.getAuthmeService() != null && ctx.getAuthmeService().isAuthmeEnabled()) {
            ctx.getAuthmeService().syncUserPasswordToAuthme(resolvedTarget, password);
        }

        if (ok) {
            ctx.getAuditService().recordPasswordChange(operator, resolvedTarget, "Admin changed user password");
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("admin.password_change_success", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.password_change_failed", language)));
        }
    }
}
