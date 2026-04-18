package team.kitemc.verifymc.user;

public record NewUserRecord(
        String username,
        String email,
        UserStatus status,
        UserStatus statusBeforeBan,
        String password,
        boolean passwordEncoded,
        Integer questionnaireScore,
        Boolean questionnairePassed,
        String questionnaireReviewSummary,
        Long questionnaireScoredAt
) {
}
