package team.kitemc.verifymc.user;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiFunction;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.WebResponseHelper;
import java.io.IOException;

public class UserStatusHandler implements HttpHandler {
    private final UserRepository userRepository;
    private final BiFunction<String, String, String> messageResolver;

    public UserStatusHandler(
            UserRepository userRepository,
            BiFunction<String, String, String> messageResolver
    ) {
        this.userRepository = userRepository;
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
                        username = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                    } else if ("language".equals(kv[0])) {
                        language = kv[1];
                    }
                }
            }
        }

        JSONObject resp = new JSONObject();
        if (username != null && !username.isBlank()) {
            UserRecord user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                resp.put("success", true);
                JSONObject data = new JSONObject();
                data.put("status", user.status().value());
                data.put("registered", true);
                data.put("username", user.username());
                resp.put("data", data);
            } else {
                resp.put("success", true);
                JSONObject data = new JSONObject();
                data.put("registered", false);
                resp.put("data", data);
            }
        } else {
            resp.put("success", false);
            resp.put("message", messageResolver.apply("error.missing_username", language));
        }
        WebResponseHelper.sendJson(exchange, resp);
    }
}

