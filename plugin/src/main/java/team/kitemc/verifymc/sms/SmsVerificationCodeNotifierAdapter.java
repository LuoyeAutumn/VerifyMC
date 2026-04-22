package team.kitemc.verifymc.sms;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.Plugin;

public class SmsVerificationCodeNotifierAdapter implements SmsVerificationCodeNotifier {
    private static final long SEND_TIMEOUT_SECONDS = 30L;

    private final Plugin plugin;
    private final SmsService smsService;

    public SmsVerificationCodeNotifierAdapter(Plugin plugin, SmsService smsService) {
        this.plugin = plugin;
        this.smsService = smsService;
    }

    @Override
    public CompletableFuture<Boolean> notifyCode(String phoneNumber, String countryCode, String code, String language) {
        try {
            String fullPhoneNumber = team.kitemc.verifymc.util.PhoneUtil.buildFullPhoneNumber(countryCode, phoneNumber);
            boolean result = smsService.sendVerificationCode(fullPhoneNumber, code, language)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return CompletableFuture.completedFuture(result);
        } catch (java.util.concurrent.TimeoutException e) {
            plugin.getLogger().warning("[VerifyMC] SMS send timed out for " + maskPhone(phoneNumber));
            return CompletableFuture.completedFuture(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(false);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            plugin.getLogger().warning("[VerifyMC] SMS send failed: " + (cause == null ? e.getMessage() : cause.getMessage()));
            return CompletableFuture.completedFuture(false);
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, 2) + "****" + phone.substring(phone.length() - 2);
    }
}
