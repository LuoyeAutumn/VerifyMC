package team.kitemc.verifymc.registration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.function.BiFunction;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.shared.EmailAddressUtil;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.WebResponseHelper;
import team.kitemc.verifymc.user.UserRepository;

/**
 * Sends an email verification code.
 * Extracted from WebServer.start() — the "/api/verify/send" context.
 */
public class VerifyCodeHandler implements HttpHandler {
    private final RegistrationPolicy registrationPolicy;
    private final UserRepository userRepository;
    private final VerifyCodeService verifyCodeService;
    private final VerificationCodeNotifier verificationCodeNotifier;
    private final BiFunction<String, String, String> messageResolver;

    public VerifyCodeHandler(
            RegistrationPolicy registrationPolicy,
            UserRepository userRepository,
            VerifyCodeService verifyCodeService,
            VerificationCodeNotifier verificationCodeNotifier,
            BiFunction<String, String, String> messageResolver
    ) {
        this.registrationPolicy = registrationPolicy;
        this.userRepository = userRepository;
        this.verifyCodeService = verifyCodeService;
        this.verificationCodeNotifier = verificationCodeNotifier;
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
        String email = EmailAddressUtil.normalize(req.optString("email", ""));
        String language = req.optString("language", "en");

        if (!registrationPolicy.usesEmailVerification()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("email.not_enabled", language)));
            return;
        }

        if (!EmailAddressUtil.isValid(email)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("email.invalid_format", language)));
            return;
        }

        if (registrationPolicy.isEmailAliasLimitEnabled() && EmailAddressUtil.hasAlias(email)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("register.alias_not_allowed", language)));
            return;
        }

        if (registrationPolicy.isEmailDomainWhitelistEnabled()) {
            String domain = EmailAddressUtil.extractDomain(email);
            if (!registrationPolicy.getEmailDomainWhitelist().contains(domain)) {
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        messageResolver.apply("register.domain_not_allowed", language)));
                return;
            }
        }

        if (userRepository.countByEmail(email) >= registrationPolicy.getMaxAccountsPerEmail()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("register.email_limit", language)));
            return;
        }

        VerifyCodeService.CodeIssueResult issueResult = verifyCodeService.issueCode(email);
        if (!issueResult.issued()) {
            long remainingSeconds = issueResult.remainingSeconds();
            String message = messageResolver.apply("email.rate_limited", language)
                    .replace("{seconds}", String.valueOf(remainingSeconds));
            JSONObject response = ApiResponseFactory.failure(message);
            response.put("remainingSeconds", remainingSeconds);
            WebResponseHelper.sendJson(exchange, response);
            return;
        }

        boolean sent = verificationCodeNotifier.sendVerificationCode(email, issueResult.code(), language);

        if (sent) {
            JSONObject response = ApiResponseFactory.success(messageResolver.apply("email.sent", language));
            response.put("remainingSeconds", issueResult.remainingSeconds());
            WebResponseHelper.sendJson(exchange, response);
        } else {
            verifyCodeService.revokeCode(email);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("email.failed", language)));
        }
    }
}

