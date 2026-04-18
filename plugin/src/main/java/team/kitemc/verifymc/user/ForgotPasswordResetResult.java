package team.kitemc.verifymc.user;

public record ForgotPasswordResetResult(
        boolean success,
        String messageKey
) {
}
