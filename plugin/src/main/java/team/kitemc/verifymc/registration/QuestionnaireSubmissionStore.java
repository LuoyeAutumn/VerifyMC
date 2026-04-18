package team.kitemc.verifymc.registration;

public interface QuestionnaireSubmissionStore {
    QuestionnaireSubmissionRecord take(String token);
}
