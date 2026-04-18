package team.kitemc.verifymc.system;

import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiConsumer;

public class SystemRoutes {
    private final HttpHandler configHandler;
    private final HttpHandler versionHandler;
    private final HttpHandler serverStatusHandler;
    private final HttpHandler downloadsHandler;
    private final HttpHandler staticFileHandler;

    public SystemRoutes(
            HttpHandler configHandler,
            HttpHandler versionHandler,
            HttpHandler serverStatusHandler,
            HttpHandler downloadsHandler,
            HttpHandler staticFileHandler
    ) {
        this.configHandler = configHandler;
        this.versionHandler = versionHandler;
        this.serverStatusHandler = serverStatusHandler;
        this.downloadsHandler = downloadsHandler;
        this.staticFileHandler = staticFileHandler;
    }

    public void register(BiConsumer<String, HttpHandler> registerApiRoute) {
        registerApiRoute.accept("/api/config", configHandler);
        registerApiRoute.accept("/api/version", versionHandler);
        registerApiRoute.accept("/api/server/status", serverStatusHandler);
        registerApiRoute.accept("/api/downloads", downloadsHandler);
    }

    public HttpHandler staticFileHandler() {
        return staticFileHandler;
    }
}
