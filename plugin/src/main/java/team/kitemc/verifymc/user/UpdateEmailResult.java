package team.kitemc.verifymc.user;

public record UpdateEmailResult(
        boolean success,
        String messageKey
) {
}
