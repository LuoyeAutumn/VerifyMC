package team.kitemc.verifymc.user;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.platform.WebResponseHelper;
import team.kitemc.verifymc.registration.CaptchaService;

public class ForgotPasswordCodeHandler implements HttpHandler {
    private final ConfigManager configManager;
    private final CaptchaService captchaService;
    private final SendForgotPasswordCodeUseCase sendForgotPasswordCodeUseCase;
    private final java.util.function.BiFunction<String, String, String> messageResolver;

    public ForgotPasswordCodeHandler(
            ConfigManager configManager,
            CaptchaService captchaService,
            SendForgotPasswordCodeUseCase sendForgotPasswordCodeUseCase,
            java.util.function.BiFunction<String, String, String> messageResolver
    ) {
        this.configManager = configManager;
        this.captchaService = captchaService;
        this.sendForgotPasswordCodeUseCase = sendForgotPasswordCodeUseCase;
        this.messageResolver = messageResolver;
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
        if (!configManager.isForgotPasswordEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("forgot_password.not_enabled", language)), 404);
            return;
        }
        if (configManager.isForgotPasswordCaptchaEnabled()) {
            String captchaToken = req.optString("captchaToken", "");
            String captchaAnswer = req.optString("captchaAnswer", "");
            if (captchaToken.isBlank() || captchaAnswer.isBlank()) {
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        messageResolver.apply("captcha.required", language)));
                return;
            }
            if (captchaService == null || !captchaService.validateCaptcha(captchaToken, captchaAnswer)) {
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        messageResolver.apply("captcha.invalid", language)));
                return;
            }
        }

        PasswordCodeSendResult result = sendForgotPasswordCodeUseCase.execute(req.optString("email", ""), language);
        String message = messageResolver.apply(result.messageKey(), language);
        if ("email.rate_limited".equals(result.messageKey())) {
            message = message.replace("{seconds}", String.valueOf(result.remainingSeconds()));
        }
        JSONObject response = result.success()
                ? ApiResponseFactory.success(message)
                : ApiResponseFactory.failure(message);
        if (result.remainingSeconds() > 0) {
            response.put("remainingSeconds", result.remainingSeconds());
        }
        if (result.multipleAccounts()) {
            response.put("multipleAccounts", true);
        }
        WebResponseHelper.sendJson(exchange, response);
    }
}
