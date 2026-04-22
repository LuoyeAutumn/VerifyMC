package team.kitemc.verifymc.sms;

import java.util.concurrent.CompletableFuture;

public interface SmsVerificationCodeNotifier {
    CompletableFuture<Boolean> notifyCode(String phoneNumber, String countryCode, String code, String language);
}
