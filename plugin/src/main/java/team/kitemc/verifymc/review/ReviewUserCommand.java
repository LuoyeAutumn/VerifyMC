package team.kitemc.verifymc.review;

public record ReviewUserCommand(
        String operator,
        String username,
        String reason
) {
}
