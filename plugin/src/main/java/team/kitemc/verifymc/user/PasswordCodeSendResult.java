package team.kitemc.verifymc.user;

public record PasswordCodeSendResult(
        boolean success,
        String messageKey,
        long remainingSeconds,
        boolean multipleAccounts
) {
    public static PasswordCodeSendResult success(String messageKey, long remainingSeconds) {
        return new PasswordCodeSendResult(true, messageKey, remainingSeconds, false);
    }

    public static PasswordCodeSendResult success(String messageKey, long remainingSeconds, boolean multipleAccounts) {
        return new PasswordCodeSendResult(true, messageKey, remainingSeconds, multipleAccounts);
    }

    public static PasswordCodeSendResult failure(String messageKey) {
        return new PasswordCodeSendResult(false, messageKey, 0, false);
    }

    public static PasswordCodeSendResult rateLimited(String messageKey, long remainingSeconds) {
        return new PasswordCodeSendResult(false, messageKey, remainingSeconds, false);
    }
}
