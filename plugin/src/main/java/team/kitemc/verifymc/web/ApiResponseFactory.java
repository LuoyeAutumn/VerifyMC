package team.kitemc.verifymc.web;

import org.json.JSONObject;

/**
 * Factory for building standardised JSON API responses.
 */
public final class ApiResponseFactory {
    private ApiResponseFactory() {}

    public static JSONObject success(String message) {
        return create(true, message);
    }

    public static JSONObject failure(String message) {
        return create(false, message);
    }

    public static JSONObject create(boolean success, String message) {
        JSONObject response = new JSONObject();
        response.put("success", success);
        response.put("message", message);
        return response;
    }

    public static JSONObject failureWithCode(String errorCode, String message) {
        JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("errorCode", errorCode);
        response.put("message", message);
        return response;
    }

    public static JSONObject failureWithAttempts(String message, int remainingAttempts) {
        JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("message", message);
        response.put("remainingAttempts", remainingAttempts);
        return response;
    }

    public static JSONObject failureWithRetryAfter(String message, long retryAfter) {
        JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("message", message);
        response.put("retryAfter", retryAfter);
        return response;
    }

    public static JSONObject failureWithDetails(String errorCode, String message, Integer remainingAttempts, Long retryAfter) {
        JSONObject response = new JSONObject();
        response.put("success", false);
        if (errorCode != null) {
            response.put("errorCode", errorCode);
        }
        response.put("message", message);
        if (remainingAttempts != null) {
            response.put("remainingAttempts", remainingAttempts);
        }
        if (retryAfter != null) {
            response.put("retryAfter", retryAfter);
        }
        return response;
    }
}
