package team.kitemc.verifymc;

import java.util.logging.Logger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.admin.VmcCommandExecutor;
import team.kitemc.verifymc.bootstrap.IntegrationBootstrap;
import team.kitemc.verifymc.bootstrap.PlatformBootstrap;
import team.kitemc.verifymc.bootstrap.PluginRuntime;
import team.kitemc.verifymc.bootstrap.RepositoryBootstrap;
import team.kitemc.verifymc.bootstrap.SchedulerBootstrap;
import team.kitemc.verifymc.bootstrap.UseCaseBootstrap;
import team.kitemc.verifymc.bootstrap.WebBootstrap;
import team.kitemc.verifymc.user.PlayerLoginListener;

public class VerifyMC extends JavaPlugin {
    private PluginRuntime runtime;
    private Metrics metrics;

    @Override
    public void onEnable() {
        Logger log = getLogger();
        try {
            runtime = initializeRuntime(log);
            registerRuntimeBindings();
            initMetrics(log);
            if (runtime.versionCheckService() != null) {
                runtime.versionCheckService().checkAsync();
            }
            log.info("[VerifyMC] Plugin enabled successfully!");
        } catch (Exception e) {
            log.severe("[VerifyMC] Plugin startup failed: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.shutdown();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
        getLogger().info("[VerifyMC] Plugin disabled.");
    }

    protected PluginRuntime initializeRuntime(Logger log) {
        PlatformBootstrap.Result platformModule = null;
        RepositoryBootstrap.Result repositoryModule = null;
        IntegrationBootstrap.Result integrationModule = null;
        SchedulerBootstrap.SchedulerModule schedulerModule = null;
        UseCaseBootstrap.Result useCaseModule = null;
        WebBootstrap.Result webModule = null;

        try {
            platformModule = new PlatformBootstrap().bootstrap(this);
            repositoryModule = new RepositoryBootstrap().bootstrap(this, platformModule.platform(), log);
            integrationModule = new IntegrationBootstrap().bootstrap(
                    this,
                    platformModule.platform(),
                    repositoryModule.userRepository(),
                    repositoryModule.auditService(),
                    platformModule.adminAccessManager(),
                    log
            );
            schedulerModule = new SchedulerBootstrap().bootstrap(
                    this,
                    platformModule.platform().getConfigManager(),
                    integrationModule.authmeService(),
                    integrationModule.webAuthHelper(),
                    log
            );
            useCaseModule = new UseCaseBootstrap().bootstrap(platformModule, repositoryModule, integrationModule);
            webModule = new WebBootstrap().bootstrap(this, platformModule, repositoryModule, integrationModule, useCaseModule);

            return new PluginRuntime(
                    log,
                    platformModule.platform(),
                    repositoryModule.userRepository(),
                    repositoryModule.auditService(),
                    platformModule.adminAccessManager(),
                    platformModule.usernameRuleService(),
                    useCaseModule.registerUserUseCase(),
                    useCaseModule.approveUserUseCase(),
                    useCaseModule.rejectUserUseCase(),
                    useCaseModule.deleteUserUseCase(),
                    useCaseModule.banUserUseCase(),
                    useCaseModule.unbanUserUseCase(),
                    useCaseModule.resetUserPasswordUseCase(),
                    useCaseModule.updateEmailUseCase(),
                    useCaseModule.listUsersUseCase(),
                    webModule.webServer(),
                    integrationModule.wsServer(),
                    integrationModule.verifyCodeService(),
                    integrationModule.captchaService(),
                    integrationModule.discordService(),
                    integrationModule.webAuthHelper(),
                    schedulerModule,
                    integrationModule.versionCheckService()
            );
        } catch (RuntimeException e) {
            cleanupPartialRuntime(log, repositoryModule, integrationModule, schedulerModule, webModule);
            throw e;
        } catch (Exception e) {
            cleanupPartialRuntime(log, repositoryModule, integrationModule, schedulerModule, webModule);
            throw new IllegalStateException("Failed to initialize runtime", e);
        }
    }

    private void cleanupPartialRuntime(
            Logger log,
            RepositoryBootstrap.Result repositoryModule,
            IntegrationBootstrap.Result integrationModule,
            SchedulerBootstrap.SchedulerModule schedulerModule,
            WebBootstrap.Result webModule
    ) {
        PluginRuntime.shutdownResources(
                log,
                webModule != null ? webModule.webServer() : null,
                integrationModule != null ? integrationModule.wsServer() : null,
                schedulerModule,
                integrationModule != null ? integrationModule.webAuthHelper() : null,
                integrationModule != null ? integrationModule.verifyCodeService() : null,
                integrationModule != null ? integrationModule.captchaService() : null,
                integrationModule != null ? integrationModule.discordService() : null,
                repositoryModule != null ? repositoryModule.userRepository() : null,
                repositoryModule != null ? repositoryModule.auditService() : null
        );
    }

    private void registerRuntimeBindings() {
        getServer().getPluginManager().registerEvents(
                new PlayerLoginListener(runtime.platform(), runtime.userRepository()),
                this
        );

        PluginCommand vmcCommand = getVmcCommand();
        if (vmcCommand != null) {
            VmcCommandExecutor executor = new VmcCommandExecutor(
                    runtime.platform(),
                    runtime.adminAccessManager(),
                    runtime.usernameRuleService(),
                    runtime.userRepository(),
                    runtime.approveUserUseCase(),
                    runtime.rejectUserUseCase(),
                    runtime.deleteUserUseCase(),
                    runtime.banUserUseCase(),
                    runtime.unbanUserUseCase(),
                    runtime.resetUserPasswordUseCase(),
                    runtime.listUsersUseCase()
            );
            vmcCommand.setExecutor(executor);
            vmcCommand.setTabCompleter(executor);
        }
    }

    protected PluginCommand getVmcCommand() {
        return getCommand("vmc");
    }

    private void initMetrics(Logger log) {
        try {
            metrics = new Metrics(this, 21854);
        } catch (Exception e) {
            log.warning("[VerifyMC] Metrics init failed: " + e.getMessage());
        }
    }
}
