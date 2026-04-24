package team.kitemc.verifymc.service;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import team.kitemc.verifymc.core.ConfigManager;
import team.kitemc.verifymc.registration.VerifyCodePurpose;
import team.kitemc.verifymc.util.EmailAddressUtil;

public class VerifyCodeService {
    private final int maxAttempts;
    private final long expireMillis;

    private final ConcurrentHashMap<String, CodeEntry> codeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> rateLimitMap = new ConcurrentHashMap<>();
    private final long rateLimitMillis = 60 * 1000;
    private final boolean debug;
    private final org.bukkit.plugin.Plugin plugin;
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile boolean running = true;
    private AuditService auditService;

    public VerifyCodeService(org.bukkit.plugin.Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        this.maxAttempts = configManager.getVerificationCodeMaxAttempts();
        this.expireMillis = configManager.getVerificationCodeExpireMinutes() * 60 * 1000L;
        startCleanupTask();
    }

    public VerifyCodeService() {
        this.plugin = null;
        this.debug = false;
        this.maxAttempts = 5;
        this.expireMillis = 5 * 60 * 1000L;
        startCleanupTask();
    }

    public void setAuditService(AuditService auditService) {
        this.auditService = auditService;
    }

    private void startCleanupTask() {
        Thread cleanupThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(300000);
                    cleanupExpiredEntries();
                } catch (InterruptedException e) {
                    debugLog("Cleanup task interrupted");
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("VerifyCodeService-Cleanup");
        cleanupThread.start();
        debugLog("Cleanup task started");
    }

    public void stop() {
        running = false;
        debugLog("Cleanup task stopped");
    }

    private void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();

        codeMap.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expire < currentTime;
            if (expired) {
                debugLog("Removed expired code for key: " + entry.getKey());
            }
            return expired;
        });

        rateLimitMap.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue()) > rateLimitMillis;
            if (expired) {
                debugLog("Removed expired rate limit for email: " + entry.getKey());
            }
            return expired;
        });

        debugLog("Cleanup completed. Active codes: " + codeMap.size() + ", Active rate limits: " + rateLimitMap.size());
    }

    private void debugLog(String msg) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[DEBUG] VerifyCodeService: " + msg);
        }
    }

    public void infoLog(String msg) {
        if (plugin != null) {
            plugin.getLogger().info("[VerifyMC] " + msg);
        }
    }

    public void warnLog(String msg) {
        if (plugin != null) {
            plugin.getLogger().warning("[VerifyMC] " + msg);
        }
    }

    private String buildStorageKey(VerifyCodePurpose purpose, String email) {
        return purpose.key() + ":" + EmailAddressUtil.normalize(email);
    }

    private String buildSmsStorageKey(VerifyCodePurpose purpose, String phone, String countryCode) {
        return purpose.key() + ":sms:" + countryCode + ":" + phone;
    }

    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***" + domain;
        }
        return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + domain;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "***";
        }
        if (phone.length() <= 4) {
            return "***" + phone.substring(phone.length() - 1);
        }
        return "***" + phone.substring(phone.length() - 4);
    }

    private void recordAudit(String eventType, String operator, String target, String detail, String clientIp) {
        if (auditService == null) {
            return;
        }
        try {
            String detailWithIp = clientIp != null && !clientIp.isEmpty() 
                ? detail + " [IP: " + clientIp + "]" 
                : detail;
            auditService.record(
                team.kitemc.verifymc.db.AuditEventType.valueOf(eventType),
                operator,
                target,
                detailWithIp
            );
        } catch (IllegalArgumentException e) {
            warnLog("Invalid audit event type: " + eventType);
        }
    }

    public CodeIssueResult issueCode(String email) {
        return issueCode(VerifyCodePurpose.REGISTER, email);
    }

    public CodeIssueResult issueCode(VerifyCodePurpose purpose, String email) {
        String key = buildStorageKey(purpose, email);
        long currentTime = System.currentTimeMillis();
        AtomicReference<CodeIssueResult> resultRef = new AtomicReference<>();

        rateLimitMap.compute(key, (k, lastSentTime) -> {
            if (lastSentTime != null) {
                long remainingMillis = rateLimitMillis - (currentTime - lastSentTime);
                long remainingSeconds = toRemainingSeconds(remainingMillis);
                if (remainingSeconds > 0) {
                    resultRef.set(CodeIssueResult.rateLimited(remainingSeconds));
                    return lastSentTime;
                }
            }

            String code = String.format("%06d", secureRandom.nextInt(1000000));
            long expireTime = currentTime + expireMillis;
            codeMap.put(k, new CodeEntry(code, expireTime));
            resultRef.set(CodeIssueResult.issued(code, toRemainingSeconds(rateLimitMillis)));
            debugLog("Issued code for key: " + k + ", expires at: " + expireTime + ", rate limit recorded at: " + currentTime);
            return currentTime;
        });

        return resultRef.get();
    }

    public String generateCode(String key) {
        CodeIssueResult result = issueCode(VerifyCodePurpose.REGISTER, key);
        return result.issued() ? result.code() : null;
    }

    public void revokeCode(String key) {
        revokeCode(VerifyCodePurpose.REGISTER, key);
    }

    public void revokeCode(VerifyCodePurpose purpose, String key) {
        String normalizedKey = buildStorageKey(purpose, key);
        codeMap.remove(normalizedKey);
        rateLimitMap.remove(normalizedKey);
        debugLog("Revoked code and cooldown for key: " + normalizedKey);
    }

    public boolean checkCode(String key, String code) {
        return checkCode(VerifyCodePurpose.REGISTER, key, code, null, null);
    }

    public boolean checkCode(VerifyCodePurpose purpose, String key, String code) {
        return checkCode(purpose, key, code, null, null);
    }

    public boolean checkCode(String key, String code, String operator, String clientIp) {
        return checkCode(VerifyCodePurpose.REGISTER, key, code, operator, clientIp);
    }

    public boolean checkCode(VerifyCodePurpose purpose, String key, String code, String operator, String clientIp) {
        String normalizedKey = buildStorageKey(purpose, key);
        debugLog("checkCode called: key=" + normalizedKey + ", code=" + code);
        CodeEntry entry = codeMap.get(normalizedKey);
        if (entry == null) {
            debugLog("No code found for key: " + normalizedKey);
            warnLog("Verification code validation failed for " + maskEmail(key) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": code not found");
            recordAudit("VERIFY_CODE_INVALID", 
                operator != null ? operator : "unknown", 
                maskEmail(key), 
                "Verification code not found for purpose: " + purpose.key(), 
                clientIp);
            return false;
        }

        if (entry.attempts >= maxAttempts) {
            debugLog("Too many attempts for key: " + normalizedKey + ", attempts: " + entry.attempts);
            codeMap.remove(normalizedKey);
            warnLog("Verification code validation failed for " + maskEmail(key) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": max attempts exceeded");
            recordAudit("VERIFY_CODE_ATTEMPTS_EXCEEDED", 
                operator != null ? operator : "unknown", 
                maskEmail(key), 
                "Max attempts (" + maxAttempts + ") exceeded for purpose: " + purpose.key(), 
                clientIp);
            return false;
        }

        if (entry.expire < System.currentTimeMillis()) {
            debugLog("Code expired for key: " + normalizedKey + ", expired at: " + entry.expire);
            codeMap.remove(normalizedKey);
            warnLog("Verification code validation failed for " + maskEmail(key) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": expired");
            recordAudit("VERIFY_CODE_EXPIRED", 
                operator != null ? operator : "unknown", 
                maskEmail(key), 
                "Verification code expired for purpose: " + purpose.key(), 
                clientIp);
            return false;
        }

        entry.attempts++;
        boolean ok = entry.code.equals(code);
        debugLog("Code verification result: " + ok + " (attempts: " + entry.attempts + ")");
        if (ok) {
            debugLog("Removing used code for key: " + normalizedKey);
            codeMap.remove(normalizedKey);
        }
        return ok;
    }

    public CodeIssueResult issueSmsCode(String phone, String countryCode, VerifyCodePurpose purpose) {
        String key = buildSmsStorageKey(purpose, phone, countryCode);
        long currentTime = System.currentTimeMillis();
        AtomicReference<CodeIssueResult> resultRef = new AtomicReference<>();

        rateLimitMap.compute(key, (k, lastSentTime) -> {
            if (lastSentTime != null) {
                long remainingMillis = rateLimitMillis - (currentTime - lastSentTime);
                long remainingSeconds = toRemainingSeconds(remainingMillis);
                if (remainingSeconds > 0) {
                    resultRef.set(CodeIssueResult.rateLimited(remainingSeconds));
                    return lastSentTime;
                }
            }

            String code = String.format("%06d", secureRandom.nextInt(1000000));
            long expireTime = currentTime + expireMillis;
            codeMap.put(k, new CodeEntry(code, expireTime));
            resultRef.set(CodeIssueResult.issued(code, toRemainingSeconds(rateLimitMillis)));
            debugLog("Issued SMS code for key: " + k + ", expires at: " + expireTime + ", rate limit recorded at: " + currentTime);
            return currentTime;
        });

        return resultRef.get();
    }

    public boolean checkSmsCode(String phone, String countryCode, String code, VerifyCodePurpose purpose) {
        return checkSmsCode(phone, countryCode, code, purpose, null, null);
    }

    public boolean checkSmsCode(String phone, String countryCode, String code, VerifyCodePurpose purpose, String operator, String clientIp) {
        String normalizedKey = buildSmsStorageKey(purpose, phone, countryCode);
        debugLog("checkSmsCode called: key=" + normalizedKey + ", code=" + code);
        CodeEntry entry = codeMap.get(normalizedKey);
        if (entry == null) {
            debugLog("No SMS code found for key: " + normalizedKey);
            warnLog("SMS verification code validation failed for " + maskPhone(phone) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": code not found");
            recordAudit("VERIFY_CODE_INVALID", 
                operator != null ? operator : "unknown", 
                maskPhone(phone), 
                "SMS verification code not found for purpose: " + purpose.key(), 
                clientIp);
            return false;
        }

        if (entry.attempts >= maxAttempts) {
            debugLog("Too many attempts for SMS key: " + normalizedKey + ", attempts: " + entry.attempts);
            codeMap.remove(normalizedKey);
            warnLog("SMS verification code validation failed for " + maskPhone(phone) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": max attempts exceeded");
            recordAudit("VERIFY_CODE_ATTEMPTS_EXCEEDED", 
                operator != null ? operator : "unknown", 
                maskPhone(phone), 
                "Max attempts (" + maxAttempts + ") exceeded for SMS purpose: " + purpose.key(), 
                clientIp);
            return false;
        }

        if (entry.expire < System.currentTimeMillis()) {
            debugLog("SMS code expired for key: " + normalizedKey + ", expired at: " + entry.expire);
            codeMap.remove(normalizedKey);
            warnLog("SMS verification code validation failed for " + maskPhone(phone) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": expired");
            recordAudit("VERIFY_CODE_EXPIRED", 
                operator != null ? operator : "unknown", 
                maskPhone(phone), 
                "SMS verification code expired for purpose: " + purpose.key(), 
                clientIp);
            return false;
        }

        entry.attempts++;
        boolean ok = entry.code.equals(code);
        debugLog("SMS code verification result: " + ok + " (attempts: " + entry.attempts + ")");
        if (ok) {
            debugLog("Removing used SMS code for key: " + normalizedKey);
            codeMap.remove(normalizedKey);
        }
        return ok;
    }

    public void revokeSmsCode(String phone, String countryCode, VerifyCodePurpose purpose) {
        String normalizedKey = buildSmsStorageKey(purpose, phone, countryCode);
        codeMap.remove(normalizedKey);
        rateLimitMap.remove(normalizedKey);
        debugLog("Revoked SMS code and cooldown for key: " + normalizedKey);
    }

    public int getRemainingAttempts(String key) {
        return getRemainingAttempts(VerifyCodePurpose.REGISTER, key);
    }

    public int getRemainingAttempts(VerifyCodePurpose purpose, String key) {
        String normalizedKey = buildStorageKey(purpose, key);
        CodeEntry entry = codeMap.get(normalizedKey);
        if (entry == null) {
            return 0;
        }
        return Math.max(0, maxAttempts - entry.attempts);
    }

    public int getRemainingSmsAttempts(String phone, String countryCode, VerifyCodePurpose purpose) {
        String normalizedKey = buildSmsStorageKey(purpose, phone, countryCode);
        CodeEntry entry = codeMap.get(normalizedKey);
        if (entry == null) {
            return 0;
        }
        return Math.max(0, maxAttempts - entry.attempts);
    }

    public VerifyResult checkCodeWithResult(String key, String code) {
        return checkCodeWithResult(VerifyCodePurpose.REGISTER, key, code, null, null);
    }

    public VerifyResult checkCodeWithResult(VerifyCodePurpose purpose, String key, String code, String operator, String clientIp) {
        String normalizedKey = buildStorageKey(purpose, key);
        debugLog("checkCodeWithResult called: key=" + normalizedKey + ", code=" + code);
        CodeEntry entry = codeMap.get(normalizedKey);
        
        if (entry == null) {
            debugLog("No code found for key: " + normalizedKey);
            warnLog("Verification code validation failed for " + maskEmail(key) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": code not found");
            recordAudit("VERIFY_CODE_INVALID", 
                operator != null ? operator : "unknown", 
                maskEmail(key), 
                "Verification code not found for purpose: " + purpose.key(), 
                clientIp);
            return new VerifyResult(false, "INVALID", 0);
        }

        if (entry.attempts >= maxAttempts) {
            debugLog("Too many attempts for key: " + normalizedKey + ", attempts: " + entry.attempts);
            codeMap.remove(normalizedKey);
            warnLog("Verification code validation failed for " + maskEmail(key) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": max attempts exceeded");
            recordAudit("VERIFY_CODE_ATTEMPTS_EXCEEDED", 
                operator != null ? operator : "unknown", 
                maskEmail(key), 
                "Max attempts (" + maxAttempts + ") exceeded for purpose: " + purpose.key(), 
                clientIp);
            return new VerifyResult(false, "ATTEMPTS_EXCEEDED", 0);
        }

        if (entry.expire < System.currentTimeMillis()) {
            debugLog("Code expired for key: " + normalizedKey + ", expired at: " + entry.expire);
            codeMap.remove(normalizedKey);
            warnLog("Verification code validation failed for " + maskEmail(key) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": expired");
            recordAudit("VERIFY_CODE_EXPIRED", 
                operator != null ? operator : "unknown", 
                maskEmail(key), 
                "Verification code expired for purpose: " + purpose.key(), 
                clientIp);
            return new VerifyResult(false, "EXPIRED", 0);
        }

        entry.attempts++;
        int remainingAttempts = Math.max(0, maxAttempts - entry.attempts);
        boolean ok = entry.code.equals(code);
        debugLog("Code verification result: " + ok + " (attempts: " + entry.attempts + ", remaining: " + remainingAttempts + ")");
        
        if (ok) {
            debugLog("Removing used code for key: " + normalizedKey);
            codeMap.remove(normalizedKey);
            return new VerifyResult(true, null, 0);
        }
        
        return new VerifyResult(false, "INVALID", remainingAttempts);
    }

    public VerifyResult checkSmsCodeWithResult(String phone, String countryCode, String code, VerifyCodePurpose purpose) {
        return checkSmsCodeWithResult(phone, countryCode, code, purpose, null, null);
    }

    public VerifyResult checkSmsCodeWithResult(String phone, String countryCode, String code, VerifyCodePurpose purpose, String operator, String clientIp) {
        String normalizedKey = buildSmsStorageKey(purpose, phone, countryCode);
        debugLog("checkSmsCodeWithResult called: key=" + normalizedKey + ", code=" + code);
        CodeEntry entry = codeMap.get(normalizedKey);
        
        if (entry == null) {
            debugLog("No SMS code found for key: " + normalizedKey);
            warnLog("SMS verification code validation failed for " + maskPhone(phone) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": code not found");
            recordAudit("VERIFY_CODE_INVALID", 
                operator != null ? operator : "unknown", 
                maskPhone(phone), 
                "SMS verification code not found for purpose: " + purpose.key(), 
                clientIp);
            return new VerifyResult(false, "INVALID", 0);
        }

        if (entry.attempts >= maxAttempts) {
            debugLog("Too many attempts for SMS key: " + normalizedKey + ", attempts: " + entry.attempts);
            codeMap.remove(normalizedKey);
            warnLog("SMS verification code validation failed for " + maskPhone(phone) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": max attempts exceeded");
            recordAudit("VERIFY_CODE_ATTEMPTS_EXCEEDED", 
                operator != null ? operator : "unknown", 
                maskPhone(phone), 
                "Max attempts (" + maxAttempts + ") exceeded for SMS purpose: " + purpose.key(), 
                clientIp);
            return new VerifyResult(false, "ATTEMPTS_EXCEEDED", 0);
        }

        if (entry.expire < System.currentTimeMillis()) {
            debugLog("SMS code expired for key: " + normalizedKey + ", expired at: " + entry.expire);
            codeMap.remove(normalizedKey);
            warnLog("SMS verification code validation failed for " + maskPhone(phone) + " from IP " + (clientIp != null ? clientIp : "unknown") + ": expired");
            recordAudit("VERIFY_CODE_EXPIRED", 
                operator != null ? operator : "unknown", 
                maskPhone(phone), 
                "SMS verification code expired for purpose: " + purpose.key(), 
                clientIp);
            return new VerifyResult(false, "EXPIRED", 0);
        }

        entry.attempts++;
        int remainingAttempts = Math.max(0, maxAttempts - entry.attempts);
        boolean ok = entry.code.equals(code);
        debugLog("SMS code verification result: " + ok + " (attempts: " + entry.attempts + ", remaining: " + remainingAttempts + ")");
        
        if (ok) {
            debugLog("Removing used SMS code for key: " + normalizedKey);
            codeMap.remove(normalizedKey);
            return new VerifyResult(true, null, 0);
        }
        
        return new VerifyResult(false, "INVALID", remainingAttempts);
    }

    private long toRemainingSeconds(long remainingMillis) {
        if (remainingMillis <= 0) {
            return 0;
        }
        return (remainingMillis + 999) / 1000;
    }

    public record CodeIssueResult(boolean issued, String code, long remainingSeconds) {
        public static CodeIssueResult issued(String code, long remainingSeconds) {
            return new CodeIssueResult(true, code, remainingSeconds);
        }

        public static CodeIssueResult rateLimited(long remainingSeconds) {
            return new CodeIssueResult(false, null, remainingSeconds);
        }
    }

    public record VerifyResult(boolean success, String failureReason, int remainingAttempts) {
        public static final String INVALID = "INVALID";
        public static final String EXPIRED = "EXPIRED";
        public static final String ATTEMPTS_EXCEEDED = "ATTEMPTS_EXCEEDED";
    }

    static class CodeEntry {
        String code;
        long expire;
        int attempts;

        CodeEntry(String code, long expire) {
            this.code = code;
            this.expire = expire;
            this.attempts = 0;
        }
    }
}
