package team.kitemc.verifymc.registration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;
import org.json.JSONObject;

public class AuthMethodValidator {
    private final List<String> mustAuthMethods;
    private final List<String> optionAuthMethods;
    private final int minOptionAuthMethods;

    public AuthMethodValidator(FileConfiguration config) {
        this.mustAuthMethods = config.getStringList("auth.must_auth_methods");
        this.optionAuthMethods = config.getStringList("auth.option_auth_methods");
        this.minOptionAuthMethods = config.getInt("auth.min_option_auth_methods", 0);
    }

    public AuthMethodValidator(List<String> mustAuthMethods, List<String> optionAuthMethods, int minOptionAuthMethods) {
        this.mustAuthMethods = mustAuthMethods != null ? mustAuthMethods : new ArrayList<>();
        this.optionAuthMethods = optionAuthMethods != null ? optionAuthMethods : new ArrayList<>();
        this.minOptionAuthMethods = minOptionAuthMethods;
    }

    public ValidationResult validate(AuthValidationContext context) {
        Set<String> completedMethods = new HashSet<>();
        List<String> missingMustMethods = new ArrayList<>();

        for (String method : mustAuthMethods) {
            if (isMethodCompleted(method, context)) {
                completedMethods.add(method);
            } else {
                missingMustMethods.add(method);
            }
        }

        if (!missingMustMethods.isEmpty()) {
            String firstMissing = missingMustMethods.get(0);
            return ValidationResult.reject(getMissingMustMessageKey(firstMissing), buildMethodResponse(firstMissing));
        }

        int optionCount = 0;
        for (String method : optionAuthMethods) {
            if (isMethodCompleted(method, context)) {
                completedMethods.add(method);
                optionCount++;
            }
        }

        if (optionCount < minOptionAuthMethods) {
            return ValidationResult.reject("auth.insufficient_optional", buildOptionsResponse(optionCount, minOptionAuthMethods));
        }

        return ValidationResult.pass(completedMethods);
    }

    private boolean isMethodCompleted(String method, AuthValidationContext context) {
        return switch (method.toLowerCase()) {
            case "email" -> context.emailVerified();
            case "sms" -> context.smsVerified();
            case "captcha" -> context.captchaVerified();
            default -> false;
        };
    }

    private String getMissingMustMessageKey(String method) {
        return switch (method.toLowerCase()) {
            case "email" -> "verify.email_required";
            case "sms" -> "verify.sms_required";
            case "captcha" -> "captcha.required";
            default -> "auth.method_required";
        };
    }

    private JSONObject buildMethodResponse(String method) {
        JSONObject response = new JSONObject();
        response.put("requiredMethod", method);
        return response;
    }

    private JSONObject buildOptionsResponse(int completed, int required) {
        JSONObject response = new JSONObject();
        response.put("completedOptional", completed);
        response.put("requiredOptional", required);
        return response;
    }

    public List<String> getMustAuthMethods() {
        return new ArrayList<>(mustAuthMethods);
    }

    public List<String> getOptionAuthMethods() {
        return new ArrayList<>(optionAuthMethods);
    }

    public int getMinOptionAuthMethods() {
        return minOptionAuthMethods;
    }

    public boolean requiresEmail() {
        return mustAuthMethods.contains("email") || optionAuthMethods.contains("email");
    }

    public boolean requiresSms() {
        return mustAuthMethods.contains("sms") || optionAuthMethods.contains("sms");
    }

    public boolean requiresCaptcha() {
        return mustAuthMethods.contains("captcha") || optionAuthMethods.contains("captcha");
    }

    public boolean isEmailMust() {
        return mustAuthMethods.contains("email");
    }

    public boolean isSmsMust() {
        return mustAuthMethods.contains("sms");
    }

    public boolean isCaptchaMust() {
        return mustAuthMethods.contains("captcha");
    }

    public record AuthValidationContext(
            boolean emailVerified,
            boolean smsVerified,
            boolean captchaVerified
    ) {
        public static AuthValidationContext of(boolean emailVerified, boolean smsVerified, boolean captchaVerified) {
            return new AuthValidationContext(emailVerified, smsVerified, captchaVerified);
        }

        public static AuthValidationContext empty() {
            return new AuthValidationContext(false, false, false);
        }
    }

    public record ValidationResult(
            boolean passed,
            String messageKey,
            JSONObject responseFields,
            Set<String> completedMethods
    ) {
        public static ValidationResult pass(Set<String> completedMethods) {
            return new ValidationResult(true, null, new JSONObject(), completedMethods);
        }

        public static ValidationResult reject(String messageKey, JSONObject responseFields) {
            return new ValidationResult(false, messageKey, responseFields != null ? responseFields : new JSONObject(), new HashSet<>());
        }
    }
}
