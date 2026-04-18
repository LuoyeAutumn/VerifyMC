package team.kitemc.verifymc.registration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiFunction;
import java.util.logging.Level;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.WebResponseHelper;
import java.io.IOException;

public class CaptchaHandler implements HttpHandler {
    private final org.bukkit.plugin.Plugin plugin;
    private final CaptchaService captchaService;
    private final BiFunction<String, String, String> messageResolver;

    public CaptchaHandler(
            org.bukkit.plugin.Plugin plugin,
            CaptchaService captchaService,
            BiFunction<String, String, String> messageResolver
    ) {
        this.plugin = plugin;
        this.captchaService = captchaService;
        this.messageResolver = messageResolver;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        String query = exchange.getRequestURI().getQuery();
        String language = "en";
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "language".equals(kv[0])) {
                    language = kv[1];
                }
            }
        }

        try {
            var result = captchaService.generateCaptcha();
            JSONObject resp = new JSONObject();
            resp.put("success", true);
            resp.put("token", result.token());
            resp.put("image", result.imageBase64());
            WebResponseHelper.sendJson(exchange, resp);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Captcha generation failed", e);
            JSONObject resp = new JSONObject();
            resp.put("success", false);
            resp.put("message", messageResolver.apply("captcha.generate_failed", language));
            WebResponseHelper.sendJson(exchange, resp, 500);
        }
    }
}

