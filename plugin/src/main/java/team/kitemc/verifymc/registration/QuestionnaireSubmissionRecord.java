package team.kitemc.verifymc.registration;

import org.json.JSONArray;
import org.json.JSONObject;

public record QuestionnaireSubmissionRecord(
        boolean passed,
        int score,
        int passScore,
        JSONArray details,
        boolean manualReviewRequired,
        boolean scoringServiceUnavailable,
        JSONObject answers,
        long submittedAt,
        long expiresAt
) {
    private static final long QUESTIONNAIRE_SUBMISSION_TTL_MS = 10 * 60 * 1000L;

    public static QuestionnaireSubmissionRecord of(
            boolean passed,
            int score,
            int passScore,
            JSONArray details,
            boolean manualReviewRequired,
            boolean scoringServiceUnavailable,
            JSONObject answers,
            long submittedAt
    ) {
        return new QuestionnaireSubmissionRecord(
                passed,
                score,
                passScore,
                details,
                manualReviewRequired,
                scoringServiceUnavailable,
                answers,
                submittedAt,
                submittedAt + QUESTIONNAIRE_SUBMISSION_TTL_MS
        );
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
