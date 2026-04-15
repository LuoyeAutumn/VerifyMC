package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.service.VerifyCodeService;
import team.kitemc.verifymc.util.PhoneUtil;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

/**
 * Handles SMS verification code sending requests at /api/verify/sms/send.
 * Validates phone numbers, enforces IP-based rate limiting, issues codes
 * via VerifyCodeService, and sends them through SmsService.
 */
public class SmsVerifyCodeHandler implements HttpHandler {
    private static final ConcurrentHashMap<String, List<Long>> ipRateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_SMS_PER_IP_PER_MINUTE = 5;
    private static final long IP_RATE_WINDOW_MS = 60_000;
    private static final long SMS_SEND_TIMEOUT_SECONDS = 15;
    private final PluginContext ctx;

    public SmsVerifyCodeHandler(PluginContext ctx) {
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

        if (!ctx.getConfigManager().isSmsAuthEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.not_enabled", language)));
            return;
        }

        String clientIp = resolveClientIp(exchange);
        if (!checkIpRateLimit(clientIp)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.ip_rate_limited", language)));
            return;
        }

        if (phone == null || phone.trim().isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.phone_required", language)));
            return;
        }
        if (!PhoneUtil.isValidPhoneNumber(phone, ctx.getConfigManager().getSmsPhoneRegex())) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.invalid_phone", language)));
            return;
        }

        List<String> validCountryCodes = ctx.getConfigManager().getCountryCodes();
        if (countryCode.isEmpty() || !validCountryCodes.contains(countryCode)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.invalid_country_code", language)));
            return;
        }

        String fullPhone = PhoneUtil.buildFullPhoneNumber(countryCode, phone);

        if (ctx.getUserDao().countUsersByPhone(fullPhone) >= ctx.getConfigManager().getMaxAccountsPerPhone()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.phone_limit", language)));
            return;
        }

        VerifyCodeService.CodeIssueResult issueResult = ctx.getVerifyCodeService().issueSmsCode(fullPhone);
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

        boolean sent;
        try {
            sent = ctx.getSmsService().sendVerificationCode(phone, countryCode, issueResult.code(), language)
                    .get(SMS_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            ctx.getPlugin().getLogger().warning("[VerifyMC] SMS send timed out for " + PhoneUtil.maskPhone(phone));
            sent = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sent = false;
        } catch (ExecutionException e) {
            ctx.getPlugin().getLogger().warning("[VerifyMC] SMS send failed: " + e.getCause().getMessage());
            sent = false;
        }

        if (sent) {
            JSONObject response = ApiResponseFactory.success(ctx.getMessage("sms.sent", language));
            response.put("remainingSeconds", issueResult.remainingSeconds());
            WebResponseHelper.sendJson(exchange, response);
        } else {
            ctx.getVerifyCodeService().revokeSmsCode(fullPhone);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("sms.failed", language)));
        }
    }

    private String resolveClientIp(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            String ip = forwarded.split(",")[0].trim();
            if (!ip.isEmpty()) return ip;
        }
        String realIp = exchange.getRequestHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private boolean checkIpRateLimit(String clientIp) {
        long now = System.currentTimeMillis();
        List<Long> timestamps = ipRateLimitMap.get(clientIp);
        if (timestamps == null) return true;
        int count = 0;
        for (long ts : timestamps) {
            if (now - ts < IP_RATE_WINDOW_MS) count++;
        }
        return count < MAX_SMS_PER_IP_PER_MINUTE;
    }

    private void recordIpRequest(String clientIp) {
        long now = System.currentTimeMillis();
        ipRateLimitMap.compute(clientIp, (ip, existing) -> {
            if (existing == null) {
                List<Long> list = new ArrayList<>();
                list.add(now);
                return list;
            }
            existing.removeIf(ts -> now - ts >= IP_RATE_WINDOW_MS);
            existing.add(now);
            return existing;
        });
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        ipRateLimitMap.entrySet().removeIf(entry -> {
            List<Long> timestamps = entry.getValue();
            timestamps.removeIf(ts -> now - ts >= IP_RATE_WINDOW_MS);
            return timestamps.isEmpty();
        });
    }
}
