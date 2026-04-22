package team.kitemc.verifymc.web;

import org.json.JSONObject;
import team.kitemc.verifymc.util.EmailAddressUtil;
import team.kitemc.verifymc.util.UsernameRuleService;

public record RegistrationRequest(
        String email,
        String code,
        String username,
        String normalizedUsername,
        String password,
        String captchaToken,
        String captchaAnswer,
        String language,
        String platform,
        JSONObject questionnaire,
        String phone,
        String countryCode,
        String smsCode
) {
    public static RegistrationRequest fromJson(JSONObject req, UsernameRuleService usernameRules) {
        String email = EmailAddressUtil.normalize(req.optString("email", ""));
        String code = req.optString("code");
        String username = req.optString("username");
        String password = req.optString("password", "");
        String captchaToken = req.optString("captchaToken", "");
        String captchaAnswer = req.optString("captchaAnswer", "");
        String language = req.optString("language", "en");
        String platform = req.optString("platform", "java");
        JSONObject questionnaire = req.optJSONObject("questionnaire");
        String normalizedUsername = usernameRules.normalize(username, platform);
        String phone = req.optString("phone", "");
        String countryCode = req.optString("countryCode", "+86");
        String smsCode = req.optString("smsCode", "");
        return new RegistrationRequest(email, code, username, normalizedUsername, password, captchaToken, captchaAnswer, language, platform, questionnaire, phone, countryCode, smsCode);
    }
}
