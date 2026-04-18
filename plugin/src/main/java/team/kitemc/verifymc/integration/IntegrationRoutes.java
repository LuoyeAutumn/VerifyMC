package team.kitemc.verifymc.integration;

import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiConsumer;

public class IntegrationRoutes {
    private final HttpHandler discordAuthHandler;
    private final HttpHandler discordCallbackHandler;
    private final HttpHandler discordStatusHandler;
    private final HttpHandler discordUnlinkHandler;

    public IntegrationRoutes(
            HttpHandler discordAuthHandler,
            HttpHandler discordCallbackHandler,
            HttpHandler discordStatusHandler,
            HttpHandler discordUnlinkHandler
    ) {
        this.discordAuthHandler = discordAuthHandler;
        this.discordCallbackHandler = discordCallbackHandler;
        this.discordStatusHandler = discordStatusHandler;
        this.discordUnlinkHandler = discordUnlinkHandler;
    }

    public void register(BiConsumer<String, HttpHandler> registerApiRoute) {
        registerApiRoute.accept("/api/discord/auth", discordAuthHandler);
        registerApiRoute.accept("/api/discord/callback", discordCallbackHandler);
        registerApiRoute.accept("/api/discord/status", discordStatusHandler);
        registerApiRoute.accept("/api/discord/unlink", discordUnlinkHandler);
    }
}
