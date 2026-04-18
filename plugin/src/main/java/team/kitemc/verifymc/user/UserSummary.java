package team.kitemc.verifymc.user;

public record UserSummary(
        String username,
        String email,
        UserStatus status,
        long regTime,
        String discordId,
        Integer questionnaireScore,
        Boolean questionnairePassed,
        String questionnaireReviewSummary,
        Long questionnaireScoredAt
) {
}
