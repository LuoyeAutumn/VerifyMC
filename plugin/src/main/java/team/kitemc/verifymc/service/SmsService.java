package team.kitemc.verifymc.service;

import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;
import team.kitemc.verifymc.core.ConfigManager;
import team.kitemc.verifymc.util.PhoneUtil;

public class SmsService implements AutoCloseable {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final boolean debug;
    private final SmsProvider provider;

    public SmsService(Plugin plugin, ConfigManager configManager, SmsProvider provider) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debug = configManager.isDebug();
        this.provider = provider;
    }

    public boolean isValidPhoneNumber(String phone) {
        return PhoneUtil.isValidPhoneNumber(phone, configManager.getSmsPhoneRegex());
    }

    public static String normalizePhoneNumber(String phone) {
        return PhoneUtil.normalizePhoneNumber(phone);
    }

    public CompletableFuture<Boolean> sendVerificationCode(String phoneNumber, String countryCode, String code, String language) {
        if (!isValidPhoneNumber(phoneNumber)) {
            debugLog("Invalid phone number: " + maskPhone(phoneNumber));
            return CompletableFuture.completedFuture(false);
        }
        return provider.sendVerificationCode(phoneNumber, countryCode, code, language);
    }

    @Override
    public void close() {
        provider.close();
    }

    private static String maskPhone(String phone) {
        return PhoneUtil.maskPhone(phone);
    }

    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] SmsService: " + msg);
        }
    }
}
