package team.kitemc.verifymc.user;

import team.kitemc.verifymc.platform.ConfigManager;

public class ConfigUserPasswordPolicy implements UserPasswordPolicy {
    private final ConfigManager configManager;

    public ConfigUserPasswordPolicy(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public String getPasswordRegex() {
        return configManager.getAuthmePasswordRegex();
    }
}
