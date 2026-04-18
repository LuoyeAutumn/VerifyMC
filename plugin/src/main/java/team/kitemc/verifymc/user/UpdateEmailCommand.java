package team.kitemc.verifymc.user;

public record UpdateEmailCommand(
        String username,
        String email
) {
}
