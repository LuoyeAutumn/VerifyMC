package team.kitemc.verifymc.registration;

import java.util.Map;

public class InMemoryQuestionnaireSubmissionStore implements QuestionnaireSubmissionStore {
    private final Map<String, QuestionnaireSubmissionRecord> records;

    public InMemoryQuestionnaireSubmissionStore(Map<String, QuestionnaireSubmissionRecord> records) {
        this.records = records;
    }

    @Override
    public QuestionnaireSubmissionRecord take(String token) {
        return records.remove(token);
    }
}
