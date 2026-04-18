package team.kitemc.verifymc.bootstrap;

import javax.net.ssl.SSLContext;
import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.admin.AdminAuditHandler;
import team.kitemc.verifymc.admin.AdminAuthmeSyncPort;
import team.kitemc.verifymc.admin.AdminRoutes;
import team.kitemc.verifymc.admin.AdminSyncHandler;
import team.kitemc.verifymc.admin.AdminUserApproveHandler;
import team.kitemc.verifymc.admin.AdminUserBanHandler;
import team.kitemc.verifymc.admin.AdminUserDeleteHandler;
import team.kitemc.verifymc.admin.AdminUserListHandler;
import team.kitemc.verifymc.admin.AdminUserPasswordHandler;
import team.kitemc.verifymc.admin.AdminUserRejectHandler;
import team.kitemc.verifymc.admin.AdminUserUnbanHandler;
import team.kitemc.verifymc.admin.AdminVerifyHandler;
import team.kitemc.verifymc.admin.AuthmeAdminSyncPortAdapter;
import team.kitemc.verifymc.integration.DiscordAuthHandler;
import team.kitemc.verifymc.integration.DiscordCallbackHandler;
import team.kitemc.verifymc.integration.DiscordStatusHandler;
import team.kitemc.verifymc.integration.DiscordUnlinkHandler;
import team.kitemc.verifymc.integration.IntegrationRoutes;
import team.kitemc.verifymc.platform.ApiRouter;
import team.kitemc.verifymc.platform.PlatformServices;
import team.kitemc.verifymc.platform.ServerSslContextFactory;
import team.kitemc.verifymc.platform.WebServer;
import team.kitemc.verifymc.questionnaire.QuestionnaireConfigHandler;
import team.kitemc.verifymc.questionnaire.QuestionnaireRoutes;
import team.kitemc.verifymc.questionnaire.QuestionnaireSubmitHandler;
import team.kitemc.verifymc.registration.CaptchaHandler;
import team.kitemc.verifymc.registration.ConfigRegistrationPolicy;
import team.kitemc.verifymc.registration.MailVerificationCodeNotifier;
import team.kitemc.verifymc.registration.RegistrationPolicy;
import team.kitemc.verifymc.registration.RegistrationProcessingHandler;
import team.kitemc.verifymc.registration.RegistrationRoutes;
import team.kitemc.verifymc.registration.VerifyCodeHandler;
import team.kitemc.verifymc.review.ReviewRoutes;
import team.kitemc.verifymc.review.ReviewStatusHandler;
import team.kitemc.verifymc.system.ConfigHandler;
import team.kitemc.verifymc.system.DownloadsHandler;
import team.kitemc.verifymc.system.ServerStatusHandler;
import team.kitemc.verifymc.system.StaticFileHandler;
import team.kitemc.verifymc.system.SystemRoutes;
import team.kitemc.verifymc.system.VersionHandler;
import team.kitemc.verifymc.user.ConfigUserPasswordPolicy;
import team.kitemc.verifymc.user.LoginHandler;
import team.kitemc.verifymc.user.UserAccessSyncPort;
import team.kitemc.verifymc.user.UserAccessSyncPortAdapter;
import team.kitemc.verifymc.user.UserPasswordHandler;
import team.kitemc.verifymc.user.UserPasswordPolicy;
import team.kitemc.verifymc.user.UserRoutes;
import team.kitemc.verifymc.user.UserStatusHandler;
import team.kitemc.verifymc.user.UserUpdateHandler;

public class WebBootstrap {
    public Result bootstrap(
            JavaPlugin plugin,
            PlatformBootstrap.Result platformModule,
            RepositoryBootstrap.Result repositoryModule,
            IntegrationBootstrap.Result integrationModule,
            UseCaseBootstrap.Result useCaseModule
    ) {
        PlatformServices platform = platformModule.platform();
        RegistrationPolicy registrationPolicy = new ConfigRegistrationPolicy(platform.getConfigManager());
        UserPasswordPolicy userPasswordPolicy = new ConfigUserPasswordPolicy(platform.getConfigManager());
        UserAccessSyncPort userAccessSyncPort = new UserAccessSyncPortAdapter(
                integrationModule.whitelistService(),
                integrationModule.authmeService()
        );
        AdminAuthmeSyncPort adminAuthmeSyncPort = new AuthmeAdminSyncPortAdapter(integrationModule.authmeService());

        RegistrationRoutes registrationRoutes = new RegistrationRoutes(
                new CaptchaHandler(plugin, integrationModule.captchaService(), platform::getMessage),
                new VerifyCodeHandler(
                        registrationPolicy,
                        repositoryModule.userRepository(),
                        integrationModule.verifyCodeService(),
                        new MailVerificationCodeNotifier(integrationModule.mailService()),
                        platform::getMessage
                ),
                new RegistrationProcessingHandler(
                        useCaseModule.registerUserUseCase(),
                        platformModule.usernameRuleService(),
                        platform::getMessage
                )
        );

        QuestionnaireRoutes questionnaireRoutes = new QuestionnaireRoutes(
                new QuestionnaireConfigHandler(integrationModule.questionnaireService()),
                new QuestionnaireSubmitHandler(
                        integrationModule.questionnaireService(),
                        platform::getMessage,
                        integrationModule.questionnaireSubmissionStore()
                )
        );

        ReviewRoutes reviewRoutes = new ReviewRoutes(
                new ReviewStatusHandler(repositoryModule.userRepository(), platform::getMessage)
        );

        UserRoutes userRoutes = new UserRoutes(
                new LoginHandler(
                        plugin,
                        platform.getConfigManager(),
                        platform.getOpsManager(),
                        repositoryModule.userRepository(),
                        integrationModule.authmeService(),
                        platformModule.adminAccessManager(),
                        integrationModule.webAuthHelper(),
                        repositoryModule.auditService(),
                        platform::getMessage,
                        false
                ),
                new LoginHandler(
                        plugin,
                        platform.getConfigManager(),
                        platform.getOpsManager(),
                        repositoryModule.userRepository(),
                        integrationModule.authmeService(),
                        platformModule.adminAccessManager(),
                        integrationModule.webAuthHelper(),
                        repositoryModule.auditService(),
                        platform::getMessage,
                        true
                ),
                new UserStatusHandler(repositoryModule.userRepository(), platform::getMessage),
                new UserUpdateHandler(
                        integrationModule.authenticatedRequestContext(),
                        useCaseModule.updateEmailUseCase(),
                        platform::getMessage
                ),
                new UserPasswordHandler(
                        integrationModule.authenticatedRequestContext(),
                        userPasswordPolicy,
                        repositoryModule.userRepository(),
                        userAccessSyncPort,
                        repositoryModule.auditService()
                )
        );

        AdminRoutes adminRoutes = new AdminRoutes(
                new AdminVerifyHandler(integrationModule.authenticatedRequestContext(), platform::getMessage),
                new AdminUserListHandler(integrationModule.authenticatedRequestContext(), useCaseModule.listUsersUseCase()),
                new AdminUserApproveHandler(
                        integrationModule.authenticatedRequestContext(),
                        platformModule.usernameRuleService(),
                        repositoryModule.userRepository(),
                        useCaseModule.approveUserUseCase(),
                        platform::getMessage
                ),
                new AdminUserRejectHandler(
                        integrationModule.authenticatedRequestContext(),
                        useCaseModule.rejectUserUseCase(),
                        platform::getMessage
                ),
                new AdminUserDeleteHandler(
                        integrationModule.authenticatedRequestContext(),
                        platformModule.usernameRuleService(),
                        repositoryModule.userRepository(),
                        useCaseModule.deleteUserUseCase(),
                        platform::getMessage
                ),
                new AdminUserBanHandler(
                        integrationModule.authenticatedRequestContext(),
                        platformModule.usernameRuleService(),
                        repositoryModule.userRepository(),
                        useCaseModule.banUserUseCase(),
                        platform::getMessage
                ),
                new AdminUserUnbanHandler(
                        integrationModule.authenticatedRequestContext(),
                        useCaseModule.unbanUserUseCase(),
                        platform::getMessage
                ),
                new AdminUserPasswordHandler(
                        integrationModule.authenticatedRequestContext(),
                        useCaseModule.resetUserPasswordUseCase(),
                        platform::getMessage
                ),
                new AdminAuditHandler(integrationModule.authenticatedRequestContext(), repositoryModule.auditService()),
                new AdminSyncHandler(
                        integrationModule.authenticatedRequestContext(),
                        adminAuthmeSyncPort,
                        plugin,
                        platform::getMessage
                )
        );

        IntegrationRoutes integrationRoutes = new IntegrationRoutes(
                new DiscordAuthHandler(integrationModule.discordService(), platform::getMessage),
                new DiscordCallbackHandler(integrationModule.discordService(), platform::getMessage),
                new DiscordStatusHandler(integrationModule.discordService(), platform::getMessage),
                new DiscordUnlinkHandler(
                        integrationModule.authenticatedRequestContext(),
                        integrationModule.discordService(),
                        platform::getMessage
                )
        );

        SystemRoutes systemRoutes = new SystemRoutes(
                new ConfigHandler(
                        platform.getConfigManager(),
                        integrationModule.questionnaireService(),
                        integrationModule.discordService()
                ),
                new VersionHandler(plugin, integrationModule.versionCheckService()),
                new ServerStatusHandler(plugin, integrationModule.authenticatedRequestContext(), platform::debugLog),
                new DownloadsHandler(platform.getConfigManager(), platform::debugLog),
                new StaticFileHandler(platform.getConfigManager(), platform.getResourceManager())
        );

        ApiRouter router = new ApiRouter(
                platform,
                registrationRoutes,
                questionnaireRoutes,
                reviewRoutes,
                userRoutes,
                adminRoutes,
                integrationRoutes,
                systemRoutes
        );

        SSLContext sslContext = null;
        if (platform.getConfigManager().isSslEnabled()) {
            try {
                sslContext = createSslContext(platform.getConfigManager());
                plugin.getLogger().info("[VerifyMC] SSL context loaded successfully.");
            } catch (Exception e) {
                plugin.getLogger().severe("[VerifyMC] Failed to initialize SSL. Web layer will remain disabled: " + e.getMessage());
                return new Result(null);
            }
        }

        try {
            if (sslContext != null) {
                integrationModule.wsServer().enableSsl(sslContext);
            }
            integrationModule.wsServer().start();
            String protocol = sslContext != null ? "WSS" : "WS";
            plugin.getLogger().info("[VerifyMC] " + protocol + " WebSocket server started on port " + integrationModule.wsServer().getPort());
        } catch (Exception e) {
            plugin.getLogger().warning("[VerifyMC] WebSocket server failed to start: " + e.getMessage());
        }

        WebServer webServer = createWebServer(platform, router, sslContext);
        webServer.start();
        return new Result(webServer);
    }

    protected SSLContext createSslContext(team.kitemc.verifymc.platform.ConfigManager configManager) throws Exception {
        return ServerSslContextFactory.create(configManager);
    }

    protected WebServer createWebServer(PlatformServices platform, ApiRouter router, SSLContext sslContext) {
        return new WebServer(platform, router, sslContext);
    }

    public record Result(WebServer webServer) {
    }
}
