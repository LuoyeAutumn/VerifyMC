package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.AuditEventType;
import team.kitemc.verifymc.registration.VerifyCodePurpose;
import team.kitemc.verifymc.service.VerifyCodeService;
import team.kitemc.verifymc.sms.SmsService;
import team.kitemc.verifymc.util.EmailAddressUtil;
import team.kitemc.verifymc.util.PhoneUtil;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

public class ForgotPasswordCodeHandler implements HttpHandler {
    private final PluginContext ctx;

    public ForgotPasswordCodeHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "en")), 400);
            return;
        }
        String account = req.optString("account", "");
        String countryCode = req.optString("countryCode", "");
        String language = req.optString("language", "en");

        if (!ctx.getConfigManager().isForgotPasswordEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("forgot_password.disabled", language)));
            return;
        }

        String clientIp = resolveClientIp(exchange);

        boolean isEmail = EmailAddressUtil.isValid(account);
        boolean isPhone = false;
        String normalizedAccount = "";

        if (isEmail) {
            normalizedAccount = EmailAddressUtil.normalize(account);
        } else {
            normalizedAccount = PhoneUtil.normalizePhoneNumber(account);
            if (PhoneUtil.isValidPhoneNumber(normalizedAccount, ctx.getConfigManager().getSmsPhoneRegex())) {
                isPhone = true;
            }
        }

        if (!isEmail && !isPhone) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("email.invalid_format", language)));
            return;
        }

        if (isPhone) {
            if (!ctx.getConfigManager().isSmsEnabled()) {
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        ctx.getMessage("sms.not_enabled", language)));
                return;
            }
            if (countryCode.isEmpty()) {
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        ctx.getMessage("sms.country_code_required", language)));
                return;
            }
            if (!ctx.getConfigManager().getCountryCodes().contains(countryCode)) {
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        ctx.getMessage("sms.invalid_country_code", language)));
                return;
            }
        }

        VerifyCodeService verifyCodeService = ctx.getVerifyCodeService();
        VerifyCodeService.CodeIssueResult issueResult;
        
        if (isEmail) {
            issueResult = verifyCodeService.issueCode(VerifyCodePurpose.FORGOT_PASSWORD, normalizedAccount);
        } else {
            issueResult = verifyCodeService.issueSmsCode(normalizedAccount, countryCode, VerifyCodePurpose.SMS_FORGOT_PASSWORD);
        }
        
        if (!issueResult.issued()) {
            long remainingSeconds = issueResult.remainingSeconds();
            String message = ctx.getMessage("email.rate_limited", language)
                    .replace("{seconds}", String.valueOf(remainingSeconds));
            JSONObject response = ApiResponseFactory.failure(message);
            response.put("remainingSeconds", remainingSeconds);
            WebResponseHelper.sendJson(exchange, response);
            return;
        }

        boolean sent = false;
        if (isEmail) {
            sent = ctx.getMailService().sendVerificationCode(normalizedAccount, issueResult.code(), language);
        } else if (isPhone) {
            String fullPhone = PhoneUtil.buildFullPhoneNumber(countryCode, normalizedAccount);
            SmsService smsService = ctx.getSmsService();
            if (smsService != null) {
                sent = smsService.sendVerificationCode(fullPhone, issueResult.code(), language).join();
            }
        }

        if (sent) {
            if (isEmail) {
                ctx.getPlugin().getLogger().info("[VerifyMC] Forgot password verification code sent to " + EmailAddressUtil.maskEmail(normalizedAccount) + " from IP " + clientIp);
            } else {
                String fullPhone = PhoneUtil.buildFullPhoneNumber(countryCode, normalizedAccount);
                ctx.getPlugin().getLogger().info("[VerifyMC] Forgot password SMS verification code sent to " + PhoneUtil.maskPhone(fullPhone) + " from IP " + clientIp);
            }
            if (ctx.getAuditService() != null) {
                if (isEmail) {
                    ctx.getAuditService().record(AuditEventType.EMAIL_SEND_SUCCESS, clientIp,
                            EmailAddressUtil.maskEmail(normalizedAccount), "Forgot password code");
                } else {
                    String fullPhone = PhoneUtil.buildFullPhoneNumber(countryCode, normalizedAccount);
                    ctx.getAuditService().record(AuditEventType.SMS_SEND_SUCCESS, clientIp,
                            PhoneUtil.maskPhone(fullPhone), "Forgot password code");
                }
            }
            JSONObject response = ApiResponseFactory.success(ctx.getMessage("forgot_password.code_sent", language));
            response.put("remainingSeconds", issueResult.remainingSeconds());
            WebResponseHelper.sendJson(exchange, response);
        } else {
            if (isEmail) {
                verifyCodeService.revokeCode(VerifyCodePurpose.FORGOT_PASSWORD, normalizedAccount);
            } else {
                verifyCodeService.revokeSmsCode(normalizedAccount, countryCode, VerifyCodePurpose.SMS_FORGOT_PASSWORD);
            }
            if (isEmail) {
                ctx.getPlugin().getLogger().warning("[VerifyMC] Forgot password verification code send failed to " + EmailAddressUtil.maskEmail(normalizedAccount) + " from IP " + clientIp);
            } else {
                String fullPhone = PhoneUtil.buildFullPhoneNumber(countryCode, normalizedAccount);
                ctx.getPlugin().getLogger().warning("[VerifyMC] Forgot password SMS verification code send failed to " + PhoneUtil.maskPhone(fullPhone) + " from IP " + clientIp);
            }
            if (ctx.getAuditService() != null) {
                if (isEmail) {
                    ctx.getAuditService().record(AuditEventType.EMAIL_SEND_FAILED, clientIp,
                            EmailAddressUtil.maskEmail(normalizedAccount), "Forgot password code failed");
                } else {
                    String fullPhone = PhoneUtil.buildFullPhoneNumber(countryCode, normalizedAccount);
                    ctx.getAuditService().record(AuditEventType.SMS_SEND_FAILED, clientIp,
                            PhoneUtil.maskPhone(fullPhone), "Forgot password code failed");
                }
            }
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("email.failed", language)));
        }
    }

    private String resolveClientIp(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            String ip = forwarded.split(",")[0].trim();
            if (!ip.isEmpty()) {
                return ip;
            }
        }
        String realIp = exchange.getRequestHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }
}
