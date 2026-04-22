package team.kitemc.verifymc.sms;

import java.util.concurrent.CompletableFuture;

public interface SmsProvider {
    CompletableFuture<Boolean> sendVerificationCode(String phoneNumber, String code, String language);
    String getProviderName();
}
