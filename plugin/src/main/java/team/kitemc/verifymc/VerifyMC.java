package team.kitemc.verifymc;

import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.core.ConfigManager;
import team.kitemc.verifymc.core.OpsManager;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.*;
import team.kitemc.verifymc.listener.PlayerLoginListener;
import team.kitemc.verifymc.command.VmcCommandExecutor;
import team.kitemc.verifymc.mail.MailService;
import team.kitemc.verifymc.service.*;
import team.kitemc.verifymc.sms.AliyunSmsProvider;
import team.kitemc.verifymc.sms.SmsService;
import team.kitemc.verifymc.sms.SmsProvider;
import team.kitemc.verifymc.sms.SmsVerificationCodeNotifierAdapter;
import team.kitemc.verifymc.sms.TencentSmsProvider;
import team.kitemc.verifymc.web.ReviewWebSocketServer;
import team.kitemc.verifymc.web.ServerSslContextFactory;
import team.kitemc.verifymc.web.WebAuthHelper;
import team.kitemc.verifymc.web.WebServer;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import java.util.logging.Logger;
import team.kitemc.verifymc.util.FoliaCompat;

/**
 * VerifyMC plugin entrypoint — refactored from the 878-line god class
 * into a clean initialization orchestrator.
 * <p>
 * Responsibilities:
 * - Initialize the {@link PluginContext} service container
 * - Wire up all services, data access, and web layer
 * - Register event listeners and command executors
 * - Manage lifecycle (enable/disable)
 */
public class VerifyMC extends JavaPlugin {
    private PluginContext context;
    private WebServer webServer;
    private ReviewWebSocketServer wsServer;
    private Metrics metrics;
    private final List<Object> scheduledTasks = new ArrayList<>();

    @Override
    public void onEnable() {
        Logger log = getLogger();

        // --- Core infrastructure ---
        context = new PluginContext(this);
        context.getResourceManager().init();
        context.getConfigManager().reloadConfig();
        context.getI18nManager().init(context.getConfigManager().getLanguage());

        // --- Data access layer ---
        initDataLayer(log);

        // --- Services ---
        initServices(log);

        // --- Web layer ---
        initWebLayer(log);

        // --- Event listeners ---
        getServer().getPluginManager().registerEvents(
                new PlayerLoginListener(context), this);

        // --- Commands ---
        var vmcCommand = getCommand("vmc");
        if (vmcCommand != null) {
            VmcCommandExecutor executor = new VmcCommandExecutor(context);
            vmcCommand.setExecutor(executor);
            vmcCommand.setTabCompleter(executor);
        }

        // --- Metrics ---
        try {
            metrics = new Metrics(this, 21854);
        } catch (Exception e) {
            log.warning("[VerifyMC] Metrics init failed: " + e.getMessage());
        }

        // --- Version check ---
        if (context.getVersionCheckService() != null) {
            context.getVersionCheckService().checkAsync();
        }

        log.info("[VerifyMC] Plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        Logger log = getLogger();

        // Stop web server
        if (webServer != null) {
            webServer.stop();
        }

        // Stop WebSocket server
        if (wsServer != null) {
            try {
                wsServer.stop(1000);
            } catch (Exception e) {
                log.warning("[VerifyMC] WebSocket server stop error: " + e.getMessage());
            }
        }

        // Stop service cleanup threads
        if (context != null) {
            FoliaCompat.cancelTasks(this, scheduledTasks);
            if (context.getWebAuthHelper() != null) {
                context.getWebAuthHelper().stopTokenCleanupTask();
            }
            if (context.getVerifyCodeService() != null) {
                context.getVerifyCodeService().stop();
            }
            if (context.getCaptchaService() != null) {
                context.getCaptchaService().stop();
            }
            context.shutdown();
        }

        // Save and close data access layer
        if (context != null) {
            if (context.getUserDao() != null) {
                context.getUserDao().save();
                context.getUserDao().close();
            }
            if (context.getAuditService() != null) {
                context.getAuditService().close();
            }
        }

        // Shutdown metrics
        if (metrics != null) {
            metrics.shutdown();
        }

        log.info("[VerifyMC] Plugin disabled.");
    }

    private void initDataLayer(Logger log) {
        ConfigManager config = context.getConfigManager();
        String storageType = config.getStorageType();
        boolean usernameCaseSensitive = config.isUsernameCaseSensitive();

        try {
            if ("mysql".equalsIgnoreCase(storageType)) {
                var props = config.getMysqlProperties();
                context.setUserDao(new MysqlUserDao(props, context.getI18nManager().getResourceBundle(), this, usernameCaseSensitive));
                context.setAuditService(new AuditService(new MysqlAuditDao(props, this)));
                log.info("[VerifyMC] Using MySQL storage.");
            } else {
                File dataDir = getDataFolder();
                context.setUserDao(new FileUserDao(new File(dataDir, "users.json"), this, usernameCaseSensitive));
                context.setAuditService(new AuditService(new FileAuditDao(new File(dataDir, "audits.json"))));
                log.info("[VerifyMC] Using file storage.");
            }
        } catch (SQLException e) {
            log.severe("[VerifyMC] Database initialization failed: " + e.getMessage());
            log.info("[VerifyMC] Falling back to file storage.");
            File dataDir = getDataFolder();
            context.setUserDao(new FileUserDao(new File(dataDir, "users.json"), this, usernameCaseSensitive));
            context.setAuditService(new AuditService(new FileAuditDao(new File(dataDir, "audits.json"))));
        }

        if (usernameCaseSensitive) {
            List<List<String>> conflictGroups = context.getUserDao().findUsernameCaseConflictGroups();
            if (!conflictGroups.isEmpty()) {
                log.severe("[VerifyMC] ========================================");
                log.severe("[VerifyMC] CRITICAL: Username case conflicts detected!");
                log.severe("[VerifyMC] The 'username_case_sensitive' option is enabled, but the following");
                log.severe("[VerifyMC] username groups have case conflicts that must be resolved manually:");
                for (List<String> group : conflictGroups) {
                    log.severe("[VerifyMC]   - " + String.join(", ", group));
                }
                log.severe("[VerifyMC] Please either:");
                log.severe("[VerifyMC]   1. Set 'username_case_sensitive: false' in config.yml, or");
                log.severe("[VerifyMC]   2. Manually resolve the conflicts by renaming/deleting users.");
                log.severe("[VerifyMC] Plugin will now disable itself.");
                log.severe("[VerifyMC] ========================================");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
    }

    private void initServices(Logger log) {
        ConfigManager config = context.getConfigManager();

        context.setOpsManager(new OpsManager(this));

        context.getResourceManager().setI18nManager(context.getI18nManager());

        // Mail service
        context.setMailService(new MailService(this, config, context.getResourceManager()));

        // Verify code service
        context.setVerifyCodeService(new VerifyCodeService(this));
        // AuthMe service
        AuthmeService authmeService = new AuthmeService(this, config);
        authmeService.setUserDao(context.getUserDao());
        context.setAuthmeService(authmeService);

        // Sync AuthMe data on startup if enabled
        if (authmeService.isAuthmeEnabled()) {
            authmeService.syncApprovedUsers();
            log.info("[VerifyMC] AuthMe sync completed on startup.");

            // Schedule periodic sync
            int syncInterval = config.getAuthmeSyncInterval();
            if (syncInterval > 0) {
                scheduledTasks.add(FoliaCompat.runTaskTimerAsync(this, () -> {
                    authmeService.syncApprovedUsers();
                }, syncInterval * 20L, syncInterval * 20L));
                log.info("[VerifyMC] AuthMe periodic sync scheduled every " + syncInterval + " seconds.");
            }
        }

        // Captcha service
        context.setCaptchaService(new CaptchaService(this));

        // Questionnaire service
        context.setQuestionnaireService(new QuestionnaireService(this));

        // Discord service
        DiscordService discordService = new DiscordService(this);
        discordService.setUserDao(context.getUserDao());
        context.setDiscordService(discordService);

        // Version check service
        context.setVersionCheckService(new VersionCheckService(this));

        // SMS service
        initSmsService(log);

        // Registration application service
        context.setRegistrationApplicationService(new RegistrationApplicationService());

        // Review application service
        context.setReviewApplicationService(new ReviewApplicationService());

        // Questionnaire application service
        context.setQuestionnaireApplicationService(new QuestionnaireApplicationService());

        // --- Web auth ---
        WebAuthHelper webAuthHelper = new WebAuthHelper(this, context.getI18nManager());
        webAuthHelper.startTokenCleanupTask();
        context.setWebAuthHelper(webAuthHelper);
    }

    private void initWebLayer(Logger log) {
        SSLContext sslContext = null;
        if (context.getConfigManager().isSslEnabled()) {
            try {
                sslContext = ServerSslContextFactory.create(context.getConfigManager());
                log.info("[VerifyMC] SSL context loaded successfully.");
            } catch (Exception e) {
                log.severe("[VerifyMC] Failed to initialize SSL. Web layer will remain disabled: " + e.getMessage());
                return;
            }
        }

        // WebSocket server for review notifications
        int wsPort = context.getConfigManager().getWsPort();
        try {
            wsServer = new ReviewWebSocketServer(wsPort, context);
            if (sslContext != null) {
                wsServer.enableSsl(sslContext);
            }
            wsServer.start();
            context.setWsServer(wsServer);
            String protocol = sslContext != null ? "WSS" : "WS";
            log.info("[VerifyMC] " + protocol + " WebSocket server started on port " + wsPort);
        } catch (Exception e) {
            log.warning("[VerifyMC] WebSocket server failed to start: " + e.getMessage());
        }

        // HTTP server
        webServer = new WebServer(context, sslContext);
        webServer.start();
    }

    private void initSmsService(Logger log) {
        ConfigManager config = context.getConfigManager();
        String providerType = config.getSmsProvider();

        SmsProvider provider;
        if ("aliyun".equalsIgnoreCase(providerType)) {
            provider = new AliyunSmsProvider(this, config);
            log.info("[VerifyMC] Using Aliyun SMS provider.");
        } else {
            provider = new TencentSmsProvider(this, config);
            log.info("[VerifyMC] Using Tencent SMS provider.");
        }

        SmsService smsService = new SmsService(this, config, provider);
        context.setSmsService(smsService);

        SmsVerificationCodeNotifierAdapter notifier = new SmsVerificationCodeNotifierAdapter(this, smsService);
        context.setSmsNotifier(notifier);
    }

    /**
     * Access the plugin context from external code.
     */
    public PluginContext getContext() {
        return context;
    }
}
