package team.kitemc.verifymc;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.admin.AdminAccessManager;
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
import team.kitemc.verifymc.admin.SimpleAuthenticatedRequestContext;
import team.kitemc.verifymc.admin.VmcCommandExecutor;
import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.audit.FileAuditDao;
import team.kitemc.verifymc.audit.MysqlAuditDao;
import team.kitemc.verifymc.integration.AuthmeService;
import team.kitemc.verifymc.integration.DiscordAuthHandler;
import team.kitemc.verifymc.integration.DiscordCallbackHandler;
import team.kitemc.verifymc.integration.DiscordService;
import team.kitemc.verifymc.integration.DiscordStatusHandler;
import team.kitemc.verifymc.integration.DiscordUnlinkHandler;
import team.kitemc.verifymc.integration.IntegrationRoutes;
import team.kitemc.verifymc.integration.MailService;
import team.kitemc.verifymc.platform.ApiRouter;
import team.kitemc.verifymc.platform.BukkitWhitelistService;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.platform.FoliaCompat;
import team.kitemc.verifymc.platform.I18nManager;
import team.kitemc.verifymc.platform.OpsManager;
import team.kitemc.verifymc.platform.PlatformServices;
import team.kitemc.verifymc.platform.ResourceManager;
import team.kitemc.verifymc.platform.ServerSslContextFactory;
import team.kitemc.verifymc.platform.WebAuthHelper;
import team.kitemc.verifymc.platform.WebServer;
import team.kitemc.verifymc.platform.WhitelistService;
import team.kitemc.verifymc.questionnaire.QuestionnaireConfigHandler;
import team.kitemc.verifymc.questionnaire.QuestionnaireRoutes;
import team.kitemc.verifymc.questionnaire.QuestionnaireService;
import team.kitemc.verifymc.questionnaire.QuestionnaireSubmitHandler;
import team.kitemc.verifymc.registration.CaptchaHandler;
import team.kitemc.verifymc.registration.CaptchaService;
import team.kitemc.verifymc.registration.AuthmeRegistrationPortAdapter;
import team.kitemc.verifymc.registration.ConfigRegistrationPolicy;
import team.kitemc.verifymc.registration.DiscordRegistrationPortAdapter;
import team.kitemc.verifymc.registration.InMemoryQuestionnaireSubmissionStore;
import team.kitemc.verifymc.registration.MailVerificationCodeNotifier;
import team.kitemc.verifymc.registration.QuestionnaireSubmissionRecord;
import team.kitemc.verifymc.registration.RegisterUserUseCase;
import team.kitemc.verifymc.registration.RegistrationAuthPort;
import team.kitemc.verifymc.registration.RegistrationDiscordPort;
import team.kitemc.verifymc.registration.RegistrationPolicy;
import team.kitemc.verifymc.registration.RegistrationProcessingHandler;
import team.kitemc.verifymc.registration.RegistrationRoutes;
import team.kitemc.verifymc.registration.UsernameRuleService;
import team.kitemc.verifymc.registration.VerifyCodeHandler;
import team.kitemc.verifymc.registration.VerifyCodeService;
import team.kitemc.verifymc.review.ApproveUserUseCase;
import team.kitemc.verifymc.review.MailReviewNotificationPortAdapter;
import team.kitemc.verifymc.review.RejectUserUseCase;
import team.kitemc.verifymc.review.ReviewApprovalPort;
import team.kitemc.verifymc.review.ReviewApprovalPortAdapter;
import team.kitemc.verifymc.review.ReviewEventPublisher;
import team.kitemc.verifymc.review.ReviewLanguageProvider;
import team.kitemc.verifymc.review.ReviewNotificationPort;
import team.kitemc.verifymc.review.ReviewRoutes;
import team.kitemc.verifymc.review.ReviewStatusHandler;
import team.kitemc.verifymc.review.ReviewWebSocketServer;
import team.kitemc.verifymc.review.WebSocketReviewEventPublisher;
import team.kitemc.verifymc.system.ConfigHandler;
import team.kitemc.verifymc.system.DownloadsHandler;
import team.kitemc.verifymc.system.ServerStatusHandler;
import team.kitemc.verifymc.system.StaticFileHandler;
import team.kitemc.verifymc.system.SystemRoutes;
import team.kitemc.verifymc.system.VersionCheckService;
import team.kitemc.verifymc.system.VersionHandler;
import team.kitemc.verifymc.user.FileUserDao;
import team.kitemc.verifymc.user.BanUserUseCase;
import team.kitemc.verifymc.user.DeleteUserUseCase;
import team.kitemc.verifymc.user.ListUsersUseCase;
import team.kitemc.verifymc.user.LoginHandler;
import team.kitemc.verifymc.user.MysqlUserDao;
import team.kitemc.verifymc.user.PlayerLoginListener;
import team.kitemc.verifymc.user.ResetUserPasswordUseCase;
import team.kitemc.verifymc.user.UnbanUserUseCase;
import team.kitemc.verifymc.user.UpdateEmailUseCase;
import team.kitemc.verifymc.user.ConfigUserEmailPolicy;
import team.kitemc.verifymc.user.ConfigUserPasswordPolicy;
import team.kitemc.verifymc.user.UserAccessSyncPort;
import team.kitemc.verifymc.user.UserAccessSyncPortAdapter;
import team.kitemc.verifymc.user.UserEmailPolicy;
import team.kitemc.verifymc.user.UserPasswordPolicy;
import team.kitemc.verifymc.user.UserPasswordHandler;
import team.kitemc.verifymc.user.UserRepository;
import team.kitemc.verifymc.user.UserRoutes;
import team.kitemc.verifymc.user.UserStatusHandler;
import team.kitemc.verifymc.user.UserUpdateHandler;

public class VerifyMC extends JavaPlugin {
    private final List<Object> scheduledTasks = new ArrayList<>();
    private final ConcurrentHashMap<String, QuestionnaireSubmissionRecord> questionnaireSubmissionStore =
            new ConcurrentHashMap<>();

    private PlatformServices platform;
    private UserRepository userRepository;
    private AuditService auditService;
    private MailService mailService;
    private VerifyCodeService verifyCodeService;
    private AuthmeService authmeService;
    private CaptchaService captchaService;
    private QuestionnaireService questionnaireService;
    private DiscordService discordService;
    private VersionCheckService versionCheckService;
    private WebAuthHelper webAuthHelper;
    private WhitelistService whitelistService;
    private ReviewWebSocketServer wsServer;
    private WebServer webServer;
    private Metrics metrics;

    private AdminAccessManager adminAccessManager;
    private UsernameRuleService usernameRuleService;
    private SimpleAuthenticatedRequestContext authenticatedRequestContext;

    private RegisterUserUseCase registerUserUseCase;
    private ApproveUserUseCase approveUserUseCase;
    private RejectUserUseCase rejectUserUseCase;
    private DeleteUserUseCase deleteUserUseCase;
    private BanUserUseCase banUserUseCase;
    private UnbanUserUseCase unbanUserUseCase;
    private ResetUserPasswordUseCase resetUserPasswordUseCase;
    private UpdateEmailUseCase updateEmailUseCase;
    private ListUsersUseCase listUsersUseCase;

    private RegistrationRoutes registrationRoutes;
    private QuestionnaireRoutes questionnaireRoutes;
    private ReviewRoutes reviewRoutes;
    private UserRoutes userRoutes;
    private AdminRoutes adminRoutes;
    private IntegrationRoutes integrationRoutes;
    private SystemRoutes systemRoutes;

    @Override
    public void onEnable() {
        Logger log = getLogger();

        initPlatform();
        buildRepositories(log);
        buildIntegrations(log);
        buildRealtimeEndpoints();
        buildUseCases();
        buildHandlersAndCommands();
        startServers(log);

        getServer().getPluginManager().registerEvents(new PlayerLoginListener(platform, userRepository), this);

        var vmcCommand = getCommand("vmc");
        if (vmcCommand != null) {
            VmcCommandExecutor executor = new VmcCommandExecutor(
                    platform,
                    adminAccessManager,
                    usernameRuleService,
                    userRepository,
                    approveUserUseCase,
                    rejectUserUseCase,
                    deleteUserUseCase,
                    banUserUseCase,
                    unbanUserUseCase,
                    resetUserPasswordUseCase,
                    listUsersUseCase
            );
            vmcCommand.setExecutor(executor);
            vmcCommand.setTabCompleter(executor);
        }

        try {
            metrics = new Metrics(this, 21854);
        } catch (Exception e) {
            log.warning("[VerifyMC] Metrics init failed: " + e.getMessage());
        }

        if (versionCheckService != null) {
            versionCheckService.checkAsync();
        }

        log.info("[VerifyMC] Plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        Logger log = getLogger();

        if (webServer != null) {
            webServer.stop();
        }

        if (wsServer != null) {
            try {
                wsServer.stop(1000);
            } catch (Exception e) {
                log.warning("[VerifyMC] WebSocket server stop error: " + e.getMessage());
            }
        }

        FoliaCompat.cancelTasks(this, scheduledTasks);
        if (webAuthHelper != null) {
            webAuthHelper.stopTokenCleanupTask();
        }
        if (verifyCodeService != null) {
            verifyCodeService.stop();
        }
        if (captchaService != null) {
            captchaService.stop();
        }
        if (discordService != null) {
            discordService.stop();
        }

        if (userRepository != null) {
            userRepository.save();
            userRepository.close();
        }
        if (auditService != null) {
            auditService.close();
        }

        if (metrics != null) {
            metrics.shutdown();
        }

        log.info("[VerifyMC] Plugin disabled.");
    }

    private void initPlatform() {
        ConfigManager configManager = new ConfigManager(this);
        I18nManager i18nManager = new I18nManager(this);
        ResourceManager resourceManager = new ResourceManager(this);
        resourceManager.setConfigManager(configManager);
        resourceManager.init();
        configManager.reloadConfig();
        i18nManager.init(configManager.getLanguage());
        resourceManager.setI18nManager(i18nManager);

        platform = new PlatformServices(
                this,
                configManager,
                i18nManager,
                resourceManager,
                new OpsManager(this)
        );
        usernameRuleService = new UsernameRuleService(configManager);
        adminAccessManager = new AdminAccessManager(platform);
    }

    private void buildRepositories(Logger log) {
        ConfigManager config = platform.getConfigManager();
        try {
            if ("mysql".equalsIgnoreCase(config.getStorageType())) {
                var props = config.getMysqlProperties();
                userRepository = new MysqlUserDao(props, platform.getI18nManager().getResourceBundle(), this);
                auditService = new AuditService(new MysqlAuditDao(props, this));
                log.info("[VerifyMC] Using MySQL storage.");
            } else {
                File dataDir = getDataFolder();
                userRepository = new FileUserDao(new File(dataDir, "users.json"), this);
                auditService = new AuditService(new FileAuditDao(new File(dataDir, "audits.json")));
                log.info("[VerifyMC] Using file storage.");
            }
        } catch (SQLException e) {
            log.severe("[VerifyMC] Database initialization failed: " + e.getMessage());
            log.info("[VerifyMC] Falling back to file storage.");
            File dataDir = getDataFolder();
            userRepository = new FileUserDao(new File(dataDir, "users.json"), this);
            auditService = new AuditService(new FileAuditDao(new File(dataDir, "audits.json")));
        }
    }

    private void buildIntegrations(Logger log) {
        ConfigManager config = platform.getConfigManager();

        whitelistService = new BukkitWhitelistService(this);
        mailService = new MailService(this, platform::getMessage);
        verifyCodeService = new VerifyCodeService(this);
        authmeService = new AuthmeService(this);
        authmeService.setUserRepository(userRepository);

        if (authmeService.isAuthmeEnabled()) {
            authmeService.syncApprovedUsers();
            log.info("[VerifyMC] AuthMe sync completed on startup.");

            int syncInterval = config.getAuthmeSyncInterval();
            if (syncInterval > 0) {
                scheduledTasks.add(FoliaCompat.runTaskTimerAsync(
                        this,
                        authmeService::syncApprovedUsers,
                        syncInterval * 20L,
                        syncInterval * 20L
                ));
                log.info("[VerifyMC] AuthMe periodic sync scheduled every " + syncInterval + " seconds.");
            }
        }

        captchaService = new CaptchaService(this);
        questionnaireService = new QuestionnaireService(this);

        discordService = new DiscordService(this);
        discordService.setUserRepository(userRepository);

        versionCheckService = new VersionCheckService(this);

        webAuthHelper = new WebAuthHelper(this, platform.getI18nManager());
        webAuthHelper.startTokenCleanupTask();
        authenticatedRequestContext = new SimpleAuthenticatedRequestContext(
                webAuthHelper,
                adminAccessManager,
                auditService,
                platform::getMessage
        );
    }

    private void buildRealtimeEndpoints() {
        wsServer = new ReviewWebSocketServer(
                platform.getConfigManager().getWsPort(),
                this,
                platform.isDebug(),
                webAuthHelper
        );
    }

    private void buildUseCases() {
        RegistrationPolicy registrationPolicy = new ConfigRegistrationPolicy(platform.getConfigManager());
        RegistrationAuthPort registrationAuthPort = new AuthmeRegistrationPortAdapter(authmeService);
        RegistrationDiscordPort registrationDiscordPort = new DiscordRegistrationPortAdapter(discordService);
        ReviewNotificationPort reviewNotificationPort = new MailReviewNotificationPortAdapter(mailService);
        ReviewApprovalPort reviewApprovalPort = new ReviewApprovalPortAdapter(whitelistService, authmeService);
        ReviewEventPublisher reviewEventPublisher = new WebSocketReviewEventPublisher(wsServer);
        ReviewLanguageProvider reviewLanguageProvider = platform.getConfigManager()::getLanguage;
        UserAccessSyncPort userAccessSyncPort = new UserAccessSyncPortAdapter(whitelistService, authmeService);
        UserEmailPolicy userEmailPolicy = new ConfigUserEmailPolicy(platform.getConfigManager());

        registerUserUseCase = new RegisterUserUseCase(
                registrationPolicy,
                verifyCodeService,
                userRepository,
                registrationAuthPort,
                captchaService,
                questionnaireService,
                registrationDiscordPort,
                whitelistService,
                usernameRuleService,
                new InMemoryQuestionnaireSubmissionStore(questionnaireSubmissionStore),
                platform::debugLog
        );
        approveUserUseCase = new ApproveUserUseCase(
                userRepository,
                auditService,
                reviewNotificationPort,
                reviewApprovalPort,
                reviewEventPublisher,
                reviewLanguageProvider
        );
        rejectUserUseCase = new RejectUserUseCase(
                userRepository,
                auditService,
                reviewNotificationPort,
                reviewEventPublisher,
                reviewLanguageProvider
        );
        deleteUserUseCase = new DeleteUserUseCase(userRepository, auditService, userAccessSyncPort);
        banUserUseCase = new BanUserUseCase(userRepository, auditService, userAccessSyncPort);
        unbanUserUseCase = new UnbanUserUseCase(userRepository, auditService, userAccessSyncPort);
        resetUserPasswordUseCase = new ResetUserPasswordUseCase(userRepository, auditService, userAccessSyncPort);
        updateEmailUseCase = new UpdateEmailUseCase(userEmailPolicy, userRepository, auditService);
        listUsersUseCase = new ListUsersUseCase(userRepository);
    }

    private void buildHandlersAndCommands() {
        RegistrationPolicy registrationPolicy = new ConfigRegistrationPolicy(platform.getConfigManager());
        UserPasswordPolicy userPasswordPolicy = new ConfigUserPasswordPolicy(platform.getConfigManager());
        UserAccessSyncPort userAccessSyncPort = new UserAccessSyncPortAdapter(whitelistService, authmeService);
        AdminAuthmeSyncPort adminAuthmeSyncPort = new AuthmeAdminSyncPortAdapter(authmeService);

        registrationRoutes = new RegistrationRoutes(
                new CaptchaHandler(this, captchaService, platform::getMessage),
                new VerifyCodeHandler(
                        registrationPolicy,
                        userRepository,
                        verifyCodeService,
                        new MailVerificationCodeNotifier(mailService),
                        platform::getMessage
                ),
                new RegistrationProcessingHandler(registerUserUseCase, usernameRuleService, platform::getMessage)
        );

        questionnaireRoutes = new QuestionnaireRoutes(
                new QuestionnaireConfigHandler(questionnaireService),
                new QuestionnaireSubmitHandler(questionnaireService, platform::getMessage, questionnaireSubmissionStore)
        );

        reviewRoutes = new ReviewRoutes(
                new ReviewStatusHandler(userRepository, platform::getMessage)
        );

        userRoutes = new UserRoutes(
                new LoginHandler(
                        this,
                        platform.getConfigManager(),
                        platform.getOpsManager(),
                        userRepository,
                        authmeService,
                        adminAccessManager,
                        webAuthHelper,
                        auditService,
                        platform::getMessage,
                        false
                ),
                new LoginHandler(
                        this,
                        platform.getConfigManager(),
                        platform.getOpsManager(),
                        userRepository,
                        authmeService,
                        adminAccessManager,
                        webAuthHelper,
                        auditService,
                        platform::getMessage,
                        true
                ),
                new UserStatusHandler(userRepository, platform::getMessage),
                new UserUpdateHandler(authenticatedRequestContext, updateEmailUseCase, platform::getMessage),
                new UserPasswordHandler(authenticatedRequestContext, userPasswordPolicy, userRepository, userAccessSyncPort, auditService)
        );

        adminRoutes = new AdminRoutes(
                new AdminVerifyHandler(authenticatedRequestContext, platform::getMessage),
                new AdminUserListHandler(authenticatedRequestContext, listUsersUseCase),
                new AdminUserApproveHandler(authenticatedRequestContext, usernameRuleService, userRepository, approveUserUseCase, platform::getMessage),
                new AdminUserRejectHandler(authenticatedRequestContext, rejectUserUseCase, platform::getMessage),
                new AdminUserDeleteHandler(authenticatedRequestContext, usernameRuleService, userRepository, deleteUserUseCase, platform::getMessage),
                new AdminUserBanHandler(authenticatedRequestContext, usernameRuleService, userRepository, banUserUseCase, platform::getMessage),
                new AdminUserUnbanHandler(authenticatedRequestContext, unbanUserUseCase, platform::getMessage),
                new AdminUserPasswordHandler(authenticatedRequestContext, resetUserPasswordUseCase, platform::getMessage),
                new AdminAuditHandler(authenticatedRequestContext, auditService),
                new AdminSyncHandler(authenticatedRequestContext, adminAuthmeSyncPort, this, platform::getMessage)
        );

        integrationRoutes = new IntegrationRoutes(
                new DiscordAuthHandler(discordService, platform::getMessage),
                new DiscordCallbackHandler(discordService, platform::getMessage),
                new DiscordStatusHandler(discordService, platform::getMessage),
                new DiscordUnlinkHandler(authenticatedRequestContext, discordService, platform::getMessage)
        );

        systemRoutes = new SystemRoutes(
                new ConfigHandler(platform.getConfigManager(), questionnaireService, discordService),
                new VersionHandler(this, versionCheckService),
                new ServerStatusHandler(this, authenticatedRequestContext, platform::debugLog),
                new DownloadsHandler(platform.getConfigManager(), platform::debugLog),
                new StaticFileHandler(platform.getConfigManager(), platform.getResourceManager())
        );
    }

    private void startServers(Logger log) {
        SSLContext sslContext = null;
        if (platform.getConfigManager().isSslEnabled()) {
            try {
                sslContext = ServerSslContextFactory.create(platform.getConfigManager());
                log.info("[VerifyMC] SSL context loaded successfully.");
            } catch (Exception e) {
                log.severe("[VerifyMC] Failed to initialize SSL. Web layer will remain disabled: " + e.getMessage());
                return;
            }
        }

        try {
            if (sslContext != null) {
                wsServer.enableSsl(sslContext);
            }
            wsServer.start();
            String protocol = sslContext != null ? "WSS" : "WS";
            log.info("[VerifyMC] " + protocol + " WebSocket server started on port " + wsServer.getPort());
        } catch (Exception e) {
            log.warning("[VerifyMC] WebSocket server failed to start: " + e.getMessage());
        }

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
        webServer = new WebServer(platform, router, sslContext);
        webServer.start();
    }
}
