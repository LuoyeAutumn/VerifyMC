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
            SmsService smsService = ctx.getSmsService();
            if (smsService != null && smsService.isValidPhoneNumber(normalizedAccount)) {
                isPhone = true;
            }
        }

        if (!isEmail && !isPhone) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("email.invalid_format", language)));
            return;
        }

        VerifyCodeService verifyCodeService = ctx.getVerifyCodeService();
        VerifyCodeService.CodeIssueResult issueResult = verifyCodeService.issueCode(VerifyCodePurpose.FORGOT_PASSWORD, normalizedAccount);
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
            SmsService smsService = ctx.getSmsService();
            if (smsService != null) {
                sent = smsService.sendVerificationCode(normalizedAccount, issueResult.code(), language).join();
            }
        }

        if (sent) {
            if (isEmail) {
                ctx.getPlugin().getLogger().info("[VerifyMC] Forgot password verification code sent to " + EmailAddressUtil.maskEmail(normalizedAccount) + " from IP " + clientIp);
            } else {
                ctx.getPlugin().getLogger().info("[VerifyMC] Forgot password verification code sent to " + PhoneUtil.maskPhone(normalizedAccount) + " from IP " + clientIp);
            }
            if (ctx.getAuditService() != null) {
                if (isEmail) {
                    ctx.getAuditService().record(AuditEventType.EMAIL_SEND_SUCCESS, clientIp,
                            EmailAddressUtil.maskEmail(normalizedAccount), "Forgot password code");
                } else {
                    ctx.getAuditService().record(AuditEventType.SMS_SEND_SUCCESS, clientIp,
                            PhoneUtil.maskPhone(normalizedAccount), "Forgot password code");
                }
            }
            JSONObject response = ApiResponseFactory.success(ctx.getMessage("forgot_password.code_sent", language));
            response.put("remainingSeconds", issueResult.remainingSeconds());
            WebResponseHelper.sendJson(exchange, response);
        } else {
            verifyCodeService.revokeCode(VerifyCodePurpose.FORGOT_PASSWORD, normalizedAccount);
            if (isEmail) {
                ctx.getPlugin().getLogger().warning("[VerifyMC] Forgot password verification code send failed to " + EmailAddressUtil.maskEmail(normalizedAccount) + " from IP " + clientIp);
            } else {
                ctx.getPlugin().getLogger().warning("[VerifyMC] Forgot password verification code send failed to " + PhoneUtil.maskPhone(normalizedAccount) + " from IP " + clientIp);
            }
            if (ctx.getAuditService() != null) {
                if (isEmail) {
                    ctx.getAuditService().record(AuditEventType.EMAIL_SEND_FAILED, clientIp,
                            EmailAddressUtil.maskEmail(normalizedAccount), "Forgot password code failed");
                } else {
                    ctx.getAuditService().record(AuditEventType.SMS_SEND_FAILED, clientIp,
                            PhoneUtil.maskPhone(normalizedAccount), "Forgot password code failed");
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
