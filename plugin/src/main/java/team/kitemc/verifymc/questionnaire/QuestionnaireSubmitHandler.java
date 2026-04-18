package team.kitemc.verifymc.questionnaire;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.WebResponseHelper;
import team.kitemc.verifymc.registration.QuestionnaireSubmissionRecord;

public class QuestionnaireSubmitHandler implements HttpHandler {
    private final QuestionnaireService questionnaireService;
    private final BiFunction<String, String, String> messageResolver;
    private final ConcurrentHashMap<String, QuestionnaireSubmissionRecord> store;

    public QuestionnaireSubmitHandler(
            QuestionnaireService questionnaireService,
            BiFunction<String, String, String> messageResolver,
            ConcurrentHashMap<String, QuestionnaireSubmissionRecord> store
    ) {
        this.questionnaireService = questionnaireService;
        this.messageResolver = messageResolver;
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("error.invalid_json", "en")), 400);
            return;
        }
        String language = req.optString("language", "en");

        if (!questionnaireService.isEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("questionnaire.not_enabled", language)));
            return;
        }

        JSONObject answers = req.optJSONObject("answers");
        if (answers == null || answers.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("register.questionnaire_required", language)));
            return;
        }

        QuestionnaireService.QuestionnaireResult result = questionnaireService.scoreAnswers(answers, language);
        int score = result.getScore();
        int passScore = result.getPassScore();
        boolean passed = result.isPassed();
        boolean manualReviewRequired = result.isManualReviewRequired();
        boolean scoringServiceUnavailable = result.isScoringServiceUnavailable();
        JSONArray details = new JSONArray();
        for (QuestionnaireService.QuestionScoreDetail detail : result.getDetails()) {
            details.put(detail.toJson());
        }

        String token = UUID.randomUUID().toString();
        long submittedAt = System.currentTimeMillis();

        QuestionnaireSubmissionRecord record = QuestionnaireSubmissionRecord.of(
                passed, score, passScore, details, manualReviewRequired,
                scoringServiceUnavailable, answers, submittedAt);
        store.put(token, record);

        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("token", token);
        resp.put("score", score);
        resp.put("passScore", passScore);
        resp.put("passed", passed);
        resp.put("manualReviewRequired", manualReviewRequired);
        resp.put("submittedAt", submittedAt);
        resp.put("expiresAt", record.expiresAt());
        if (details != null && !details.isEmpty()) {
            resp.put("details", details);
        }
        WebResponseHelper.sendJson(exchange, resp);
    }
}

