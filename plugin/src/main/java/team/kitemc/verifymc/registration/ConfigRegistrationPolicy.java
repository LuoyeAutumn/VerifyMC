package team.kitemc.verifymc.registration;

import java.util.List;
import team.kitemc.verifymc.platform.ConfigManager;

public class ConfigRegistrationPolicy implements RegistrationPolicy {
    private final ConfigManager configManager;

    public ConfigRegistrationPolicy(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public String getAuthmePasswordRegex() {
        return configManager.getAuthmePasswordRegex();
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
    public boolean isUsernameCaseSensitive() {
        return configManager.isUsernameCaseSensitive();
    }

    @Override
    public long getMaxAccountsPerEmail() {
        return configManager.getMaxAccountsPerEmail();
    }

    @Override
    public boolean usesCaptchaVerification() {
        return configManager.isCaptchaAuthEnabled();
    }

    @Override
    public boolean usesEmailVerification() {
        return configManager.isEmailAuthEnabled();
    }

    @Override
    public boolean isAutoApprove() {
        return configManager.isAutoApprove();
    }
}
