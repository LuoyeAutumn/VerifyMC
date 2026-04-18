package team.kitemc.verifymc.admin;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.WebResponseHelper;

/**
 * Shared authentication utility for admin-capable HTTP handlers.
 */
public final class AdminAuthUtil {
    private AdminAuthUtil() {
    }

    public static boolean requireAuth(HttpExchange exchange, AuthenticatedRequestContext ctx) throws IOException {
        String token = extractBearerToken(exchange);
        if (token == null || !ctx.getWebAuthHelper().isValidToken(token)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure("Unauthorized"), 401);
            return false;
        }
        return true;
    }

    public static String requireAdmin(HttpExchange exchange, AuthenticatedRequestContext ctx, AdminAction action) throws IOException {
        String token = extractBearerToken(exchange);
        if (token == null || !ctx.getWebAuthHelper().isValidToken(token)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure("Unauthorized"), 401);
            return null;
        }

        String username = ctx.getWebAuthHelper().getUsername(token);
        if (username == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure("Unauthorized"), 401);
            return null;
        }

        if (!ctx.getAdminAccessManager().canAccess(username, action)) {
            String acceptLanguage = exchange.getRequestHeaders().getFirst("Accept-Language");
            String language = (acceptLanguage != null && acceptLanguage.startsWith("zh")) ? "zh" : "en";
            if (ctx.getAuditService() != null) {
                ctx.getAuditService().recordAdminAccessDenied(username, exchange.getRequestURI().getPath());
            }
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.forbidden", language)), 403);
            return null;
        }

        return username;
    }

    public static String getAuthenticatedUser(HttpExchange exchange, AuthenticatedRequestContext ctx) throws IOException {
        String token = extractBearerToken(exchange);
        if (token == null || !ctx.getWebAuthHelper().isValidToken(token)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure("Unauthorized"), 401);
            return null;
        }
        return ctx.getWebAuthHelper().getUsername(token);
    }

    public static String getAuthenticatedUserQuietly(HttpExchange exchange, AuthenticatedRequestContext ctx) {
        try {
            String token = extractBearerToken(exchange);
            if (token == null || !ctx.getWebAuthHelper().isValidToken(token)) {
                return null;
            }
            return ctx.getWebAuthHelper().getUsername(token);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractBearerToken(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}
