package team.kitemc.verifymc.user;

import java.util.List;
import team.kitemc.verifymc.platform.ConfigManager;

public class ConfigUserEmailPolicy implements UserEmailPolicy {
    private final ConfigManager configManager;

    public ConfigUserEmailPolicy(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean requiresVerificationForEmailChange() {
        return configManager.isEmailAuthEnabled();
    }

    @Override
    public boolean isEmailAliasLimitEnabled() {
        return configManager.isEmailAliasLimitEnabled();
    }

    @Override
    public boolean isEmailDomainWhitelistEnabled() {
        return configManager.isEmailDomainWhitelistEnabled();
    }

    @Override
    public List<String> getEmailDomainWhitelist() {
        return configManager.getEmailDomainWhitelist();
    }

    @Override
    public long getMaxAccountsPerEmail() {
        return configManager.getMaxAccountsPerEmail();
    }
}
