package team.kitemc.verifymc.service;

import java.util.concurrent.CompletableFuture;

public interface SmsProvider extends AutoCloseable {
    CompletableFuture<Boolean> sendVerificationCode(String phoneNumber, String countryCode, String code, String language);

    @Override
    default void close() {
    }
}
