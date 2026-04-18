package team.kitemc.verifymc.registration;

import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiConsumer;

public class RegistrationRoutes {
    private final HttpHandler captchaHandler;
    private final HttpHandler verifyCodeHandler;
    private final HttpHandler registrationProcessingHandler;

    public RegistrationRoutes(
            HttpHandler captchaHandler,
            HttpHandler verifyCodeHandler,
            HttpHandler registrationProcessingHandler
    ) {
        this.captchaHandler = captchaHandler;
        this.verifyCodeHandler = verifyCodeHandler;
        this.registrationProcessingHandler = registrationProcessingHandler;
    }

    public void register(BiConsumer<String, HttpHandler> registerApiRoute) {
        registerApiRoute.accept("/api/captcha/generate", captchaHandler);
        registerApiRoute.accept("/api/captcha", captchaHandler);
        registerApiRoute.accept("/api/verify/send", verifyCodeHandler);
        registerApiRoute.accept("/api/register", registrationProcessingHandler);
    }
}
