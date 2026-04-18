package team.kitemc.verifymc.questionnaire;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.WebResponseHelper;

import java.io.IOException;

public class QuestionnaireConfigHandler implements HttpHandler {
    private final QuestionnaireService questionnaireService;

    public QuestionnaireConfigHandler(QuestionnaireService questionnaireService) {
        this.questionnaireService = questionnaireService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        String query = exchange.getRequestURI().getQuery();
        String lang = "en";
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "language".equals(kv[0])) {
                    lang = kv[1];
                }
            }
        }

        if (!questionnaireService.isEnabled()) {
            JSONObject resp = new JSONObject();
            resp.put("success", true);
            JSONObject data = new JSONObject();
            data.put("enabled", false);
            data.put("questions", new org.json.JSONArray());
            resp.put("data", data);
            WebResponseHelper.sendJson(exchange, resp);
            return;
        }

        JSONObject config = questionnaireService.getQuestionnaireConfig(lang);
        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("data", config);
        WebResponseHelper.sendJson(exchange, resp);
    }
}

