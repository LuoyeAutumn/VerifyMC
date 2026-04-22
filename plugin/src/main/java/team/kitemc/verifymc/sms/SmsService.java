package team.kitemc.verifymc.sms;

import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;
import team.kitemc.verifymc.core.ConfigManager;
import team.kitemc.verifymc.util.PhoneUtil;

public class SmsService {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final boolean debug;
    private final SmsProvider provider;

    public SmsService(Plugin plugin, ConfigManager configManager, SmsProvider provider) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.provider = provider;
        this.debug = configManager.isDebug();
    }

    public boolean isValidPhoneNumber(String phone) {
        return PhoneUtil.isValidPhoneNumber(phone, configManager.getSmsPhoneRegex());
    }

    public CompletableFuture<Boolean> sendVerificationCode(String phoneNumber, String code, String language) {
        if (!isValidPhoneNumber(phoneNumber)) {
            debugLog("Invalid phone number: " + PhoneUtil.maskPhone(phoneNumber));
            return CompletableFuture.completedFuture(false);
        }
        return provider.sendVerificationCode(phoneNumber, code, language);
    }

    public String getProviderName() {
        return provider.getProviderName();
    }

    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] SmsService: " + msg);
        }
    }
}
