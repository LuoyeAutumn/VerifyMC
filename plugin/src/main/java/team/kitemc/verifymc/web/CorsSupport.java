package team.kitemc.verifymc.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * Shared CORS policy helpers for API endpoints.
 */
public final class CorsSupport {
    private static final String ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    private static final String ALLOWED_HEADERS = "Content-Type, Authorization";
    private static final String VARY_VALUE = "Origin, Access-Control-Request-Method, Access-Control-Request-Headers";

    private CorsSupport() {
    }

    public static boolean isOriginAllowed(String requestOrigin, String allowedOrigin) {
        String normalizedRequestOrigin = normalizeOrigin(requestOrigin);
        String normalizedAllowedOrigin = normalizeOrigin(allowedOrigin);
        if (normalizedRequestOrigin.isEmpty() || normalizedAllowedOrigin.isEmpty()) {
            return false;
        }
        return normalizedRequestOrigin.equals(normalizedAllowedOrigin);
    }

    public static void applyCorsHeaders(HttpExchange exchange, String allowedOrigin) {
        String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
        if (!isOriginAllowed(requestOrigin, allowedOrigin)) {
            return;
        }

        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", requestOrigin);
        headers.set("Access-Control-Allow-Methods", ALLOWED_METHODS);
        headers.set("Access-Control-Allow-Headers", ALLOWED_HEADERS);
        headers.set("Vary", VARY_VALUE);
    }

    public static boolean handlePreflight(HttpExchange exchange, String allowedOrigin) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            return false;
        }

        String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
        if (!isOriginAllowed(requestOrigin, allowedOrigin)) {
            exchange.sendResponseHeaders(403, -1);
            return true;
        }

        applyCorsHeaders(exchange, allowedOrigin);
        exchange.sendResponseHeaders(204, -1);
        return true;
    }

    private static String normalizeOrigin(String origin) {
        if (origin == null) {
            return "";
        }
        return origin.trim().replaceAll("/+$", "");
    }
}
