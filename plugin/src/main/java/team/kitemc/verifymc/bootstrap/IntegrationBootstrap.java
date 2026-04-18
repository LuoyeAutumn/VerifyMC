package team.kitemc.verifymc.bootstrap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.admin.AdminAccessManager;
import team.kitemc.verifymc.admin.SimpleAuthenticatedRequestContext;
import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.integration.AuthmeService;
import team.kitemc.verifymc.integration.DiscordService;
import team.kitemc.verifymc.integration.MailService;
import team.kitemc.verifymc.platform.BukkitWhitelistService;
import team.kitemc.verifymc.platform.PlatformServices;
import team.kitemc.verifymc.platform.WebAuthHelper;
import team.kitemc.verifymc.platform.WhitelistService;
import team.kitemc.verifymc.questionnaire.QuestionnaireService;
import team.kitemc.verifymc.registration.CaptchaService;
import team.kitemc.verifymc.registration.QuestionnaireSubmissionRecord;
import team.kitemc.verifymc.registration.VerifyCodeService;
import team.kitemc.verifymc.review.ReviewWebSocketServer;
import team.kitemc.verifymc.system.VersionCheckService;
import team.kitemc.verifymc.user.UserRepository;

public class IntegrationBootstrap {
    public Result bootstrap(
            JavaPlugin plugin,
            PlatformServices platform,
            UserRepository userRepository,
            AuditService auditService,
            AdminAccessManager adminAccessManager,
            Logger log
    ) {
        WhitelistService whitelistService = new BukkitWhitelistService(plugin);
        MailService mailService = new MailService(plugin, platform::getMessage);
        VerifyCodeService verifyCodeService = new VerifyCodeService(plugin);
        AuthmeService authmeService = new AuthmeService(plugin);
        authmeService.setUserRepository(userRepository);

        if (authmeService.isAuthmeEnabled()) {
            authmeService.syncApprovedUsers();
            log.info("[VerifyMC] AuthMe sync completed on startup.");
        }

        CaptchaService captchaService = new CaptchaService(plugin);
        QuestionnaireService questionnaireService = new QuestionnaireService(plugin);

        DiscordService discordService = new DiscordService(plugin);
        discordService.setUserRepository(userRepository);

        VersionCheckService versionCheckService = new VersionCheckService(plugin);

        WebAuthHelper webAuthHelper = new WebAuthHelper(plugin, platform.getI18nManager());
        SimpleAuthenticatedRequestContext authenticatedRequestContext = new SimpleAuthenticatedRequestContext(
                webAuthHelper,
                adminAccessManager,
                auditService,
                platform::getMessage
        );
        ReviewWebSocketServer wsServer = new ReviewWebSocketServer(
                platform.getConfigManager().getWsPort(),
                plugin,
                platform.isDebug(),
                webAuthHelper
        );

        return new Result(
                mailService,
                verifyCodeService,
                authmeService,
                captchaService,
                questionnaireService,
                discordService,
                versionCheckService,
                webAuthHelper,
                whitelistService,
                wsServer,
                authenticatedRequestContext,
                new ConcurrentHashMap<String, QuestionnaireSubmissionRecord>()
        );
    }

    public record Result(
            MailService mailService,
            VerifyCodeService verifyCodeService,
            AuthmeService authmeService,
            CaptchaService captchaService,
            QuestionnaireService questionnaireService,
            DiscordService discordService,
            VersionCheckService versionCheckService,
            WebAuthHelper webAuthHelper,
            WhitelistService whitelistService,
            ReviewWebSocketServer wsServer,
            SimpleAuthenticatedRequestContext authenticatedRequestContext,
            ConcurrentHashMap<String, QuestionnaireSubmissionRecord> questionnaireSubmissionStore
    ) {
    }
}
