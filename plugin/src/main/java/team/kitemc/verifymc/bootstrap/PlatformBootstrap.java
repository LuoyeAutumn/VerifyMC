package team.kitemc.verifymc.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.admin.AdminAccessManager;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.platform.I18nManager;
import team.kitemc.verifymc.platform.OpsManager;
import team.kitemc.verifymc.platform.PlatformServices;
import team.kitemc.verifymc.platform.ResourceManager;
import team.kitemc.verifymc.registration.UsernameRuleService;

public class PlatformBootstrap {
    public Result bootstrap(JavaPlugin plugin) {
        ConfigManager configManager = new ConfigManager(plugin);
        I18nManager i18nManager = new I18nManager(plugin);
        ResourceManager resourceManager = new ResourceManager(plugin);
        resourceManager.setConfigManager(configManager);
        resourceManager.init();
        configManager.reloadConfig();
        i18nManager.init(configManager.getLanguage());
        resourceManager.setI18nManager(i18nManager);

        PlatformServices platform = new PlatformServices(
                plugin,
                configManager,
                i18nManager,
                resourceManager,
                new OpsManager(plugin)
        );
        UsernameRuleService usernameRuleService = new UsernameRuleService(configManager);
        AdminAccessManager adminAccessManager = new AdminAccessManager(platform);
        return new Result(platform, usernameRuleService, adminAccessManager);
    }

    public record Result(
            PlatformServices platform,
            UsernameRuleService usernameRuleService,
            AdminAccessManager adminAccessManager
    ) {
    }
}
