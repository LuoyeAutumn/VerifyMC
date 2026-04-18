package team.kitemc.verifymc.system;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import team.kitemc.verifymc.integration.DiscordService;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.platform.WebResponseHelper;
import team.kitemc.verifymc.questionnaire.QuestionnaireService;

/**
 * Serves front-end configuration: auth methods, theme settings, captcha/questionnaire/discord flags, etc.
 * Extracted from WebServer.start() — the "/api/config" context.
 */
public class ConfigHandler implements HttpHandler {
    private final ConfigManager configManager;
    private final QuestionnaireService questionnaireService;
    private final DiscordService discordService;

    public ConfigHandler(
            ConfigManager configManager,
            QuestionnaireService questionnaireService,
            DiscordService discordService
    ) {
        this.configManager = configManager;
        this.questionnaireService = questionnaireService;
        this.discordService = discordService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        JSONObject config = new JSONObject();
        config.put("authMethods", new JSONArray(configManager.getAuthMethods()));
        config.put("theme", configManager.getTheme());
        config.put("logoUrl", configManager.getLogoUrl());
        config.put("announcement", configManager.getAnnouncement());
        config.put("usernameRegex", configManager.getUsernameRegex());
        config.put("usernameCaseSensitive", configManager.isUsernameCaseSensitive());
        config.put("webServerPrefix", configManager.getWebServerPrefix());
        config.put("wsPort", configManager.getWsPort());

        JSONObject userConfig = new JSONObject();
        userConfig.put("passwordResetMethods", new JSONArray(configManager.getUserPasswordResetMethods()));
        config.put("user", userConfig);

        JSONObject forgotPasswordConfig = new JSONObject();
        forgotPasswordConfig.put("enabled", configManager.isForgotPasswordEnabled());
        forgotPasswordConfig.put("captchaEnabled", configManager.isForgotPasswordCaptchaEnabled());
        config.put("forgotPassword", forgotPasswordConfig);

        JSONObject authmeConfig = new JSONObject();
        authmeConfig.put("enabled", configManager.isAuthmeEnabled());
        authmeConfig.put("passwordRegex", configManager.getAuthmePasswordRegex());
        config.put("authme", authmeConfig);

        List<String> authMethods = configManager.getAuthMethods();
        boolean captchaEnabled = authMethods.contains("captcha");
        boolean emailEnabled = authMethods.contains("email");
        JSONObject captchaConfig = new JSONObject();
        captchaConfig.put("enabled", captchaEnabled);
        captchaConfig.put("emailEnabled", emailEnabled);
        captchaConfig.put("type", configManager.getCaptchaType());
        config.put("captcha", captchaConfig);

        JSONObject questionnaireConfig = new JSONObject();
        questionnaireConfig.put("enabled", questionnaireService != null && questionnaireService.isEnabled());
        questionnaireConfig.put("passScore", questionnaireService != null ? questionnaireService.getPassScore() : 60);
        questionnaireConfig.put("hasTextQuestions", questionnaireService != null && questionnaireService.hasTextQuestions());
        config.put("questionnaire", questionnaireConfig);

        JSONObject discordConfig = new JSONObject();
        discordConfig.put("enabled", discordService != null && discordService.isEnabled());
        discordConfig.put("required", discordService != null && discordService.isRequired());
        config.put("discord", discordConfig);

        JSONObject bedrockConfig = new JSONObject();
        bedrockConfig.put("enabled", configManager.isBedrockEnabled());
        bedrockConfig.put("prefix", configManager.getBedrockPrefix());
        config.put("bedrock", bedrockConfig);

        if (configManager.isEmailDomainWhitelistEnabled()) {
            config.put("emailDomainWhitelist", new JSONArray(configManager.getEmailDomainWhitelist()));
        }
        config.put("enableEmailDomainWhitelist", configManager.isEmailDomainWhitelistEnabled());
        config.put("enableEmailAliasLimit", configManager.isEmailAliasLimitEnabled());
        config.put("language", configManager.getLanguage());

        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("config", config);
        WebResponseHelper.sendJson(exchange, resp);
    }
}

