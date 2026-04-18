package team.kitemc.verifymc.registration;

import org.json.JSONObject;

public record RegisterUserResult(
        boolean success,
        RegistrationOutcome outcome,
        String messageKey,
        JSONObject responseFields
) {
    public static RegisterUserResult validationFailure(String messageKey, JSONObject responseFields) {
        return new RegisterUserResult(false, RegistrationOutcome.FAILED, messageKey, responseFields);
    }

    public static RegisterUserResult outcome(boolean success, RegistrationOutcome outcome, String messageKey) {
        return new RegisterUserResult(success, outcome, messageKey, null);
    }
}
