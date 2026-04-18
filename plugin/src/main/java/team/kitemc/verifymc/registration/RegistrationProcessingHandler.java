package team.kitemc.verifymc.registration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.function.BiFunction;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.WebResponseHelper;

public class RegistrationProcessingHandler implements HttpHandler {
    private final RegisterUserUseCase registerUserUseCase;
    private final UsernameRuleService usernameRuleService;
    private final BiFunction<String, String, String> messageResolver;

    public RegistrationProcessingHandler(
            RegisterUserUseCase registerUserUseCase,
            UsernameRuleService usernameRuleService,
            BiFunction<String, String, String> messageResolver
    ) {
        this.registerUserUseCase = registerUserUseCase;
        this.usernameRuleService = usernameRuleService;
        this.messageResolver = messageResolver;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) {
            return;
        }

        JSONObject requestJson;
        try {
            requestJson = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("error.invalid_json", "en")), 400);
            return;
        }

        RegistrationRequest request = RegistrationRequest.fromJson(requestJson, usernameRuleService);
        RegisterUserResult result = registerUserUseCase.execute(
                new RegisterUserCommand(request, java.util.UUID.randomUUID().toString())
        );
        applyResult(exchange, result, request.language());
    }

    private void applyResult(HttpExchange exchange, RegisterUserResult result, String language) throws IOException {
        String message = messageResolver.apply(result.messageKey(), language);
        JSONObject responseFields = result.responseFields();
        if (responseFields != null && responseFields.has("regex")) {
            message = message.replace("{regex}", responseFields.optString("regex", ""));
        }

        JSONObject response = result.success()
                ? ApiResponseFactory.success(message)
                : ApiResponseFactory.failure(message);
        if (responseFields != null) {
            for (String key : responseFields.keySet()) {
                if (!"regex".equals(key)) {
                    response.put(key, responseFields.get(key));
                }
            }
        }
        WebResponseHelper.sendJson(exchange, response);
    }
}
