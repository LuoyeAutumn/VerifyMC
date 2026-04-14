package team.kitemc.verifymc.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import team.kitemc.verifymc.core.PluginContext;

import java.io.IOException;

/**
 * Wraps API handlers with the configured CORS policy.
 */
public class CorsHandler implements HttpHandler {
    private final PluginContext ctx;
    private final HttpHandler delegate;

    public CorsHandler(PluginContext ctx, HttpHandler delegate) {
        this.ctx = ctx;
        this.delegate = delegate;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String allowedOrigin = ctx.getConfigManager().getAllowedOrigin();
        if (CorsSupport.handlePreflight(exchange, allowedOrigin)) {
            return;
        }

        CorsSupport.applyCorsHeaders(exchange, allowedOrigin);
        delegate.handle(exchange);
    }
}
