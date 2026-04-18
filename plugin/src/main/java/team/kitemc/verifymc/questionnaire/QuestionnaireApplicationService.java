package team.kitemc.verifymc.questionnaire;

import org.json.JSONObject;
import team.kitemc.verifymc.platform.ApiResponseFactory;

public class QuestionnaireApplicationService {
    public JSONObject buildAnswersRequiredResponse(String message) {
        return ApiResponseFactory.failure(message);
    }
}

