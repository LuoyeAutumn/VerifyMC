package team.kitemc.verifymc.bootstrap;

import java.util.logging.Logger;
import team.kitemc.verifymc.admin.AdminAccessManager;
import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.integration.DiscordService;
import team.kitemc.verifymc.platform.PlatformServices;
import team.kitemc.verifymc.platform.WebAuthHelper;
import team.kitemc.verifymc.platform.WebServer;
import team.kitemc.verifymc.registration.CaptchaService;
import team.kitemc.verifymc.registration.UsernameRuleService;
import team.kitemc.verifymc.registration.VerifyCodeService;
import team.kitemc.verifymc.review.ReviewWebSocketServer;
import team.kitemc.verifymc.system.VersionCheckService;
import team.kitemc.verifymc.user.BanUserUseCase;
import team.kitemc.verifymc.user.DeleteUserUseCase;
import team.kitemc.verifymc.user.ListUsersUseCase;
import team.kitemc.verifymc.user.ResetUserPasswordUseCase;
import team.kitemc.verifymc.user.UnbanUserUseCase;
import team.kitemc.verifymc.user.UpdateEmailUseCase;
import team.kitemc.verifymc.user.UserRepository;
import team.kitemc.verifymc.review.ApproveUserUseCase;
import team.kitemc.verifymc.review.RejectUserUseCase;
import team.kitemc.verifymc.registration.RegisterUserUseCase;

public class PluginRuntime {
    private final Logger logger;
    private final PlatformServices platform;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AdminAccessManager adminAccessManager;
    private final UsernameRuleService usernameRuleService;
    private final RegisterUserUseCase registerUserUseCase;
    private final ApproveUserUseCase approveUserUseCase;
    private final RejectUserUseCase rejectUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final BanUserUseCase banUserUseCase;
    private final UnbanUserUseCase unbanUserUseCase;
    private final ResetUserPasswordUseCase resetUserPasswordUseCase;
    private final UpdateEmailUseCase updateEmailUseCase;
    private final ListUsersUseCase listUsersUseCase;
    private final WebServer webServer;
    private final ReviewWebSocketServer wsServer;
    private final VerifyCodeService verifyCodeService;
    private final CaptchaService captchaService;
    private final DiscordService discordService;
    private final WebAuthHelper webAuthHelper;
    private final SchedulerBootstrap.SchedulerModule schedulerModule;
    private final VersionCheckService versionCheckService;
    private boolean shutdown;

    public PluginRuntime(
            Logger logger,
            PlatformServices platform,
            UserRepository userRepository,
            AuditService auditService,
            AdminAccessManager adminAccessManager,
            UsernameRuleService usernameRuleService,
            RegisterUserUseCase registerUserUseCase,
            ApproveUserUseCase approveUserUseCase,
            RejectUserUseCase rejectUserUseCase,
            DeleteUserUseCase deleteUserUseCase,
            BanUserUseCase banUserUseCase,
            UnbanUserUseCase unbanUserUseCase,
            ResetUserPasswordUseCase resetUserPasswordUseCase,
            UpdateEmailUseCase updateEmailUseCase,
            ListUsersUseCase listUsersUseCase,
            WebServer webServer,
            ReviewWebSocketServer wsServer,
            VerifyCodeService verifyCodeService,
            CaptchaService captchaService,
            DiscordService discordService,
            WebAuthHelper webAuthHelper,
            SchedulerBootstrap.SchedulerModule schedulerModule,
            VersionCheckService versionCheckService
    ) {
        this.logger = logger;
        this.platform = platform;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.adminAccessManager = adminAccessManager;
        this.usernameRuleService = usernameRuleService;
        this.registerUserUseCase = registerUserUseCase;
        this.approveUserUseCase = approveUserUseCase;
        this.rejectUserUseCase = rejectUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
        this.banUserUseCase = banUserUseCase;
        this.unbanUserUseCase = unbanUserUseCase;
        this.resetUserPasswordUseCase = resetUserPasswordUseCase;
        this.updateEmailUseCase = updateEmailUseCase;
        this.listUsersUseCase = listUsersUseCase;
        this.webServer = webServer;
        this.wsServer = wsServer;
        this.verifyCodeService = verifyCodeService;
        this.captchaService = captchaService;
        this.discordService = discordService;
        this.webAuthHelper = webAuthHelper;
        this.schedulerModule = schedulerModule;
        this.versionCheckService = versionCheckService;
    }

    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        shutdownResources(
                logger,
                webServer,
                wsServer,
                schedulerModule,
                webAuthHelper,
                verifyCodeService,
                captchaService,
                discordService,
                userRepository,
                auditService
        );
    }

    public static void shutdownResources(
            Logger log,
            WebServer webServer,
            ReviewWebSocketServer wsServer,
            SchedulerBootstrap.SchedulerModule schedulerModule,
            WebAuthHelper webAuthHelper,
            VerifyCodeService verifyCodeService,
            CaptchaService captchaService,
            DiscordService discordService,
            UserRepository userRepository,
            AuditService auditService
    ) {
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
        if (schedulerModule != null) {
            schedulerModule.shutdown();
        }
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
    }

    public PlatformServices platform() {
        return platform;
    }

    public UserRepository userRepository() {
        return userRepository;
    }

    public AdminAccessManager adminAccessManager() {
        return adminAccessManager;
    }

    public UsernameRuleService usernameRuleService() {
        return usernameRuleService;
    }

    public ApproveUserUseCase approveUserUseCase() {
        return approveUserUseCase;
    }

    public RejectUserUseCase rejectUserUseCase() {
        return rejectUserUseCase;
    }

    public DeleteUserUseCase deleteUserUseCase() {
        return deleteUserUseCase;
    }

    public BanUserUseCase banUserUseCase() {
        return banUserUseCase;
    }

    public UnbanUserUseCase unbanUserUseCase() {
        return unbanUserUseCase;
    }

    public ResetUserPasswordUseCase resetUserPasswordUseCase() {
        return resetUserPasswordUseCase;
    }

    public ListUsersUseCase listUsersUseCase() {
        return listUsersUseCase;
    }

    public UpdateEmailUseCase updateEmailUseCase() {
        return updateEmailUseCase;
    }

    public VersionCheckService versionCheckService() {
        return versionCheckService;
    }

    public RegisterUserUseCase registerUserUseCase() {
        return registerUserUseCase;
    }
}
