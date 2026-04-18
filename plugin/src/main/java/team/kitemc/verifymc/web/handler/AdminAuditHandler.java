package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.AuditEventType;
import team.kitemc.verifymc.db.AuditPage;
import team.kitemc.verifymc.db.AuditQuery;
import team.kitemc.verifymc.db.AuditRecord;
import team.kitemc.verifymc.security.AdminAction;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Returns the audit log.
 */
public class AdminAuditHandler implements HttpHandler {
    private final PluginContext ctx;

    public AdminAuditHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        // Require admin privileges
        if (AdminAuthUtil.requireAdmin(exchange, ctx, AdminAction.AUDIT) == null) return;

        AuditQuery query = parseQuery(exchange.getRequestURI().getQuery());
        AuditPage page = ctx.getAuditService().query(query);

        JSONArray items = new JSONArray();
        for (AuditRecord audit : page.items()) {
            JSONObject obj = new JSONObject();
            if (audit.id() != null) obj.put("id", audit.id());
            obj.put("action", audit.eventType().key());
            obj.put("operator", audit.operator());
            obj.put("target", audit.target());
            obj.put("detail", audit.detail());
            obj.put("occurredAt", audit.occurredAt());
            items.put(obj);
        }

        JSONObject pagination = new JSONObject();
        pagination.put("currentPage", page.currentPage());
        pagination.put("pageSize", page.pageSize());
        pagination.put("totalCount", page.totalCount());
        pagination.put("totalPages", page.totalPages());
        pagination.put("hasNext", page.hasNext());
        pagination.put("hasPrev", page.hasPrev());

        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("items", items);
        resp.put("availableActions", new JSONArray(ctx.getAuditService().getAvailableActions()));
        resp.put("pagination", pagination);
        WebResponseHelper.sendJson(exchange, resp);
    }

    private AuditQuery parseQuery(String rawQuery) {
        int page = 1;
        int size = 20;
        String keyword = "";
        AuditEventType eventType = null;

        if (rawQuery == null || rawQuery.isBlank()) {
            return new AuditQuery(eventType, keyword, page, size);
        }

        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                continue;
            }

            String key = parts[0];
            String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            switch (key) {
                case "page" -> {
                    try {
                        page = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        page = 1;
                    }
                }
                case "size" -> {
                    try {
                        size = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        size = 20;
                    }
                }
                case "keyword" -> keyword = value;
                case "action" -> eventType = AuditEventType.fromKey(value).orElse(null);
                default -> {
                }
            }
        }

        return new AuditQuery(eventType, keyword, page, size);
    }
}
