package team.kitemc.verifymc.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.util.EmailAddressUtil;
import team.kitemc.verifymc.web.handler.AdminAuditHandler;
import team.kitemc.verifymc.web.handler.AdminSyncHandler;
import team.kitemc.verifymc.web.handler.AdminUserApproveHandler;
import team.kitemc.verifymc.web.handler.AdminUserBanHandler;
import team.kitemc.verifymc.web.handler.AdminUserDeleteHandler;
import team.kitemc.verifymc.web.handler.AdminUserListHandler;
import team.kitemc.verifymc.web.handler.AdminUserPasswordHandler;
import team.kitemc.verifymc.web.handler.AdminUserRejectHandler;
import team.kitemc.verifymc.web.handler.AdminUserUnbanHandler;
import team.kitemc.verifymc.web.handler.AdminVerifyHandler;
import team.kitemc.verifymc.web.handler.CaptchaHandler;
import team.kitemc.verifymc.web.handler.ConfigHandler;
import team.kitemc.verifymc.web.handler.DiscordAuthHandler;
import team.kitemc.verifymc.web.handler.DiscordCallbackHandler;
import team.kitemc.verifymc.web.handler.DiscordStatusHandler;
import team.kitemc.verifymc.web.handler.DiscordUnlinkHandler;
import team.kitemc.verifymc.web.handler.DownloadsHandler;
import team.kitemc.verifymc.web.handler.LoginHandler;
import team.kitemc.verifymc.web.handler.QuestionnaireConfigHandler;
import team.kitemc.verifymc.web.handler.QuestionnaireSubmitHandler;
import team.kitemc.verifymc.web.handler.ReviewStatusHandler;
import team.kitemc.verifymc.web.handler.ServerStatusHandler;
import team.kitemc.verifymc.web.handler.StaticFileHandler;
import team.kitemc.verifymc.web.handler.UserPasswordHandler;
import team.kitemc.verifymc.web.handler.UserStatusHandler;
import team.kitemc.verifymc.web.handler.UserUpdateHandler;
import team.kitemc.verifymc.web.handler.VerifyCodeHandler;
import team.kitemc.verifymc.web.handler.VersionHandler;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Central API router that maps HTTP paths to modular handler classes.
 * <p>
 * Replaces the 1271-line {@code WebServer.start()} method which defined
 * all routes inline as anonymous HttpHandler classes.
 * <p>
 * Each endpoint group is now a dedicated handler class, making the
 * routing topology visible at a glance and each handler independently testable.
 */
public class ApiRouter {
    private final PluginContext ctx;
    private final ConcurrentHashMap<String, RegistrationProcessingHandler.QuestionnaireSubmissionRecord> questionnaireSubmissionStore;

    public ApiRouter(PluginContext ctx) {
        this.ctx = ctx;
        this.questionnaireSubmissionStore = new ConcurrentHashMap<>();
    }

    /**
     * Register all API routes on the given HttpServer.
     */
    public void registerRoutes(HttpServer server) {
        // --- Configuration endpoint ---
        registerApiRoute(server, "/api/config", new ConfigHandler(ctx));

        // --- Captcha endpoints ---
        registerApiRoute(server, "/api/captcha/generate", new CaptchaHandler(ctx));
        registerApiRoute(server, "/api/captcha", new CaptchaHandler(ctx));

        // --- Email verification ---
        registerApiRoute(server, "/api/verify/send", new VerifyCodeHandler(ctx));
        // --- Questionnaire endpoints ---
        registerApiRoute(server, "/api/questionnaire/config", new QuestionnaireConfigHandler(ctx));
        registerApiRoute(server, "/api/questionnaire/submit", new QuestionnaireSubmitHandler(ctx, questionnaireSubmissionStore));

        // --- Registration ---
        registerApiRoute(server, "/api/register", new RegistrationProcessingHandler(
                ctx.getPlugin(),
                ctx.getVerifyCodeService(),
                ctx.getUserDao(),
                ctx.getAuthmeService(),
                ctx.getCaptchaService(),
                ctx.getQuestionnaireService(),
                ctx.getDiscordService(),
                ctx.getRegistrationApplicationService(),
                questionnaireSubmissionStore,
                () -> ctx.getConfigManager().getEmailDomainWhitelist(),
                (key, lang) -> ctx.getMessage(key, lang),
                (username) -> hasUsernameCaseConflict(username),
                () -> ctx.getConfigManager().isUsernameCaseSensitive(),
                ctx.getUsernameRuleService(),
                (email) -> isValidEmail(email),
                (msg) -> ctx.debugLog(msg)
        ));

        // --- Review status check ---
        registerApiRoute(server, "/api/review/status", new ReviewStatusHandler(ctx));

        // --- Login endpoints ---
        registerApiRoute(server, "/api/login", new LoginHandler(ctx, false));
        registerApiRoute(server, "/api/admin/login", new LoginHandler(ctx, true));

        // --- Admin endpoints ---
        registerApiRoute(server, "/api/admin/verify", new AdminVerifyHandler(ctx));
        registerApiRoute(server, "/api/admin/users", new AdminUserListHandler(ctx));
        registerApiRoute(server, "/api/admin/user/approve", new AdminUserApproveHandler(ctx));
        registerApiRoute(server, "/api/admin/user/reject", new AdminUserRejectHandler(ctx));
        registerApiRoute(server, "/api/admin/user/delete", new AdminUserDeleteHandler(ctx));
        registerApiRoute(server, "/api/admin/user/ban", new AdminUserBanHandler(ctx));
        registerApiRoute(server, "/api/admin/user/unban", new AdminUserUnbanHandler(ctx));
        registerApiRoute(server, "/api/admin/user/password", new AdminUserPasswordHandler(ctx));
        registerApiRoute(server, "/api/admin/audits", new AdminAuditHandler(ctx));
        registerApiRoute(server, "/api/admin/sync", new AdminSyncHandler(ctx));

        // --- Discord endpoints ---
        registerApiRoute(server, "/api/discord/auth", new DiscordAuthHandler(ctx));
        registerApiRoute(server, "/api/discord/callback", new DiscordCallbackHandler(ctx));
        registerApiRoute(server, "/api/discord/status", new DiscordStatusHandler(ctx));
        registerApiRoute(server, "/api/discord/unlink", new DiscordUnlinkHandler(ctx));

        // --- Version check ---
        registerApiRoute(server, "/api/version", new VersionHandler(ctx));

        // --- User status query ---
        registerApiRoute(server, "/api/user/status", new UserStatusHandler(ctx));

        // --- Server status ---
        registerApiRoute(server, "/api/server/status", new ServerStatusHandler(ctx));

        // --- Downloads ---
        registerApiRoute(server, "/api/downloads", new DownloadsHandler(ctx));

        // --- User profile management ---
        registerApiRoute(server, "/api/user/update", new UserUpdateHandler(ctx));
        registerApiRoute(server, "/api/user/password", new UserPasswordHandler(ctx));

        // --- Static files (front-end) ---
        if (ctx.getConfigManager().isServeStaticEnabled()) {
            server.createContext("/", new CorsHandler(ctx, new StaticFileHandler(ctx)));
        }
    }

    private void registerApiRoute(HttpServer server, String path, HttpHandler handler) {
        server.createContext(path, new CorsHandler(ctx, handler));
    }

    // --- Utility methods used by route wiring (delegated from WebServer) ---

    private boolean hasUsernameCaseConflict(String username) {
        // If username is case-sensitive, no conflict check needed
        if (ctx.getConfigManager().isUsernameCaseSensitive()) {
            return false;
        }
        // If username is case-insensitive, check for case conflicts
        var existing = ctx.getUserDao().getUserByUsername(username);
        if (existing == null) return false;
        String storedName = (String) existing.get("username");
        return storedName != null && !storedName.equals(username) && storedName.equalsIgnoreCase(username);
    }

    private boolean isValidEmail(String email) {
        return EmailAddressUtil.isValid(email);
    }
}
