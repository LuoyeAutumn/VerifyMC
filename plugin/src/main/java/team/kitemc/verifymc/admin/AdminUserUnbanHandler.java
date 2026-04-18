package team.kitemc.verifymc.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiFunction;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.ApiResponseFactory;
import team.kitemc.verifymc.platform.WebResponseHelper;
import java.io.IOException;
import team.kitemc.verifymc.registration.UsernameRuleService;
import team.kitemc.verifymc.user.AdminUserCommand;
import team.kitemc.verifymc.user.AdminUserResult;
import team.kitemc.verifymc.user.UnbanUserUseCase;
import team.kitemc.verifymc.user.UserRepository;

/**
 * Unbans a user — restores status and re-adds to whitelist.
 */
public class AdminUserUnbanHandler implements HttpHandler {
    private final AuthenticatedRequestContext authContext;
    private final UsernameRuleService usernameRuleService;
    private final UserRepository userRepository;
    private final UnbanUserUseCase unbanUserUseCase;
    private final BiFunction<String, String, String> messageResolver;

    public AdminUserUnbanHandler(
            AuthenticatedRequestContext authContext,
            UsernameRuleService usernameRuleService,
            UserRepository userRepository,
            UnbanUserUseCase unbanUserUseCase,
            BiFunction<String, String, String> messageResolver
    ) {
        this.authContext = authContext;
        this.usernameRuleService = usernameRuleService;
        this.userRepository = userRepository;
        this.unbanUserUseCase = unbanUserUseCase;
        this.messageResolver = messageResolver;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        // Require admin privileges and get operator username
        String operator = AdminAuthUtil.requireAdmin(exchange, authContext, AdminAction.UNBAN);
        if (operator == null) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("error.invalid_json", "en")), 400);
            return;
        }
        String target = req.optString("username", req.optString("uuid", ""));
        String language = req.optString("language", "en");

        if (target.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("admin.missing_user_identifier", language)));
            return;
        }

        String resolvedTarget = usernameRuleService.resolveAdminTarget(target, userRepository);
        if (resolvedTarget.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("admin.invalid_username", language)));
            return;
        }

        AdminUserResult result = unbanUserUseCase.execute(new AdminUserCommand(operator, resolvedTarget, ""));
        JSONObject response = result.success()
                ? ApiResponseFactory.success(messageResolver.apply(result.messageKey(), language))
                : ApiResponseFactory.failure(messageResolver.apply(result.messageKey(), language));
        WebResponseHelper.sendJson(exchange, response);
    }
}

