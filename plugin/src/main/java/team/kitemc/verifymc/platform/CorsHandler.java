package team.kitemc.verifymc.platform;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import team.kitemc.verifymc.platform.PlatformServices;

import java.io.IOException;
import java.util.List;

/**
 * Wraps API handlers with the configured CORS policy.
 */
public class CorsHandler implements HttpHandler {
    private final PlatformServices ctx;
    private final HttpHandler delegate;

    public CorsHandler(PlatformServices ctx, HttpHandler delegate) {
        this.ctx = ctx;
        this.delegate = delegate;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<String> allowedOrigins = ctx.getConfigManager().getAllowedOrigins();
        if (CorsSupport.handlePreflight(ctx, exchange, allowedOrigins)) {
            return;
        }

        CorsSupport.applyCorsHeaders(ctx, exchange, allowedOrigins);
        delegate.handle(exchange);
    }
}

