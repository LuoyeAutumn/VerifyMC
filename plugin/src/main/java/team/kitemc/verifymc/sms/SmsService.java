package team.kitemc.verifymc.sms;

import java.util.concurrent.CompletableFuture;
import team.kitemc.verifymc.core.ConfigManager;
import team.kitemc.verifymc.util.PhoneUtil;

public class SmsService {
    private final ConfigManager configManager;
    private final SmsProvider provider;

    public SmsService(ConfigManager configManager, SmsProvider provider) {
        this.configManager = configManager;
        this.provider = provider;
    }

    public boolean isValidPhoneNumber(String phone) {
        return PhoneUtil.isValidPhoneNumber(phone, configManager.getSmsPhoneRegex());
    }

    public CompletableFuture<Boolean> sendVerificationCode(String phoneNumber, String code, String language) {
        return provider.sendVerificationCode(phoneNumber, code, language);
    }

    public String getProviderName() {
        return provider.getProviderName();
    }
}
