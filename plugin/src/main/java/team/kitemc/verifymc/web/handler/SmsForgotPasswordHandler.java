package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.AuditEventType;
import team.kitemc.verifymc.registration.VerifyCodePurpose;
import team.kitemc.verifymc.service.VerifyCodeService;
import team.kitemc.verifymc.sms.SmsService;
import team.kitemc.verifymc.util.PhoneUtil;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

public class SmsForgotPasswordHandler implements HttpHandler {
    private static final ConcurrentHashMap<String, List<Long>> IP_RATE_LIMIT_MAP = new ConcurrentHashMap<>();
    private static final int MAX_SMS_PER_IP_PER_MINUTE = 5;
    private static final long IP_RATE_WINDOW_MS = 60_000L;

    private final PluginContext ctx;

    public SmsForgotPasswordHandler(PluginContext ctx) {
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

        String phone = PhoneUtil.normalizePhoneNumber(req.optString("phone", ""));
        String countryCode = req.optString("countryCode", "");
        String language = req.optString("language", "en");

        if (!ctx.getConfigManager().isSmsEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.not_enabled", language)));
            return;
        }

        if (!ctx.getConfigManager().isForgotPasswordEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("forgot_password.disabled", language)));
            return;
        }

        String clientIp = resolveClientIp(exchange);
        if (!checkIpRateLimit(clientIp)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.ip_rate_limited", language)));
            return;
        }

        if (phone.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.phone_required", language)));
            return;
        }

        if (!PhoneUtil.isValidPhoneNumber(phone, ctx.getConfigManager().getSmsPhoneRegex())) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.invalid_phone", language)));
            return;
        }

        if (!ctx.getConfigManager().getCountryCodes().contains(countryCode)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.invalid_country_code", language)));
            return;
        }

        String fullPhone = PhoneUtil.buildFullPhoneNumber(countryCode, phone);
        if (ctx.getUserDao().getUserByPhone(fullPhone) == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.phone_not_registered", language)));
            return;
        }

        VerifyCodeService verifyCodeService = ctx.getVerifyCodeService();
        VerifyCodeService.CodeIssueResult issueResult = verifyCodeService.issueSmsCode(
                phone, countryCode, VerifyCodePurpose.SMS_FORGOT_PASSWORD);
        if (!issueResult.issued()) {
            long remainingSeconds = issueResult.remainingSeconds();
            String message = ctx.getMessage("sms.rate_limited", language)
                    .replace("{seconds}", String.valueOf(remainingSeconds));
            JSONObject response = ApiResponseFactory.failure(message);
            response.put("remainingSeconds", remainingSeconds);
            WebResponseHelper.sendJson(exchange, response);
            return;
        }

        recordIpRequest(clientIp);

        SmsService smsService = ctx.getSmsService();
        boolean sent = smsService.sendVerificationCode(fullPhone, issueResult.code(), language).join();

        if (sent) {
            ctx.getAuditService().record(AuditEventType.SMS_SEND_SUCCESS, clientIp,
                    PhoneUtil.maskPhone(fullPhone), "ForgotPassword");
            JSONObject response = ApiResponseFactory.success(ctx.getMessage("forgot_password.code_sent", language));
            response.put("remainingSeconds", issueResult.remainingSeconds());
            WebResponseHelper.sendJson(exchange, response);
        } else {
            verifyCodeService.revokeSmsCode(phone, countryCode, VerifyCodePurpose.SMS_FORGOT_PASSWORD);
            ctx.getAuditService().record(AuditEventType.SMS_SEND_FAILED, clientIp,
                    PhoneUtil.maskPhone(fullPhone), "ForgotPassword failed");
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.failed", language)));
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

    private boolean checkIpRateLimit(String clientIp) {
        long now = System.currentTimeMillis();
        List<Long> timestamps = IP_RATE_LIMIT_MAP.get(clientIp);
        if (timestamps == null) {
            return true;
        }
        int count = 0;
        for (long timestamp : timestamps) {
            if (now - timestamp < IP_RATE_WINDOW_MS) {
                count++;
            }
        }
        return count < MAX_SMS_PER_IP_PER_MINUTE;
    }

    private void recordIpRequest(String clientIp) {
        long now = System.currentTimeMillis();
        IP_RATE_LIMIT_MAP.compute(clientIp, (ip, existing) -> {
            List<Long> timestamps = existing == null ? new ArrayList<>() : existing;
            timestamps.removeIf(ts -> now - ts >= IP_RATE_WINDOW_MS);
            timestamps.add(now);
            return timestamps;
        });
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        IP_RATE_LIMIT_MAP.entrySet().removeIf(entry -> {
            List<Long> timestamps = entry.getValue();
            timestamps.removeIf(ts -> now - ts >= IP_RATE_WINDOW_MS);
            return timestamps.isEmpty();
        });
    }
}
