package team.kitemc.verifymc.user;

public record UserRecord(
        String username,
        String email,
        UserStatus status,
        UserStatus statusBeforeBan,
        String passwordHash,
        long regTime,
        String discordId,
        Integer questionnaireScore,
        Boolean questionnairePassed,
        String questionnaireReviewSummary,
        Long questionnaireScoredAt
) {
    public UserSummary toSummary() {
        return new UserSummary(
                username,
                email,
                status,
                regTime,
                discordId,
                questionnaireScore,
                questionnairePassed,
                questionnaireReviewSummary,
                questionnaireScoredAt
        );
    }
}
