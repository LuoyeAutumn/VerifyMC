package team.kitemc.verifymc.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.function.BiFunction;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.WebResponseHelper;

public class DiscordStatusHandler implements HttpHandler {
    private final DiscordService discordService;
    private final BiFunction<String, String, String> messageResolver;

    public DiscordStatusHandler(
            DiscordService discordService,
            BiFunction<String, String, String> messageResolver
    ) {
        this.discordService = discordService;
        this.messageResolver = messageResolver;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        String query = exchange.getRequestURI().getQuery();
        String username = null;
        String language = "en";
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    if ("username".equals(kv[0])) {
                        username = kv[1];
                    } else if ("language".equals(kv[0])) {
                        language = kv[1];
                    }
                }
            }
        }

        JSONObject resp = new JSONObject();
        if (username != null && !username.isBlank()) {
            boolean linked = discordService.isLinked(username);
            resp.put("success", true);
            resp.put("linked", linked);
            if (linked) {
                var discordUser = discordService.getLinkedUser(username);
                if (discordUser != null) {
                    resp.put("user", discordUser.toJson());
                }
            }
        } else {
            resp.put("success", false);
            resp.put("message", messageResolver.apply("error.missing_username", language));
        }
        WebResponseHelper.sendJson(exchange, resp);
    }
}

