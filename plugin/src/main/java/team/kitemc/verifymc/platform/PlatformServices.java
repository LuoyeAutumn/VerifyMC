package team.kitemc.verifymc.platform;

import org.bukkit.plugin.java.JavaPlugin;

public class PlatformServices {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final I18nManager i18nManager;
    private final ResourceManager resourceManager;
    private final OpsManager opsManager;

    public PlatformServices(
            JavaPlugin plugin,
            ConfigManager configManager,
            I18nManager i18nManager,
            ResourceManager resourceManager,
            OpsManager opsManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.i18nManager = i18nManager;
        this.resourceManager = resourceManager;
        this.opsManager = opsManager;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public I18nManager getI18nManager() {
        return i18nManager;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public OpsManager getOpsManager() {
        return opsManager;
    }

    public boolean isDebug() {
        return configManager.isDebug();
    }

    public void debugLog(String msg) {
        if (isDebug()) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }

    public String getMessage(String key, String language) {
        return i18nManager.getMessage(key, language);
    }
}
