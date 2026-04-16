package team.kitemc.verifymc.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;
import team.kitemc.verifymc.core.ConfigManager;
import team.kitemc.verifymc.util.PhoneUtil;

public class SmsService implements AutoCloseable {
    private static final String SERVICE = "sms";
    private static final String HOST = "sms.tencentcloudapi.com";
    private static final String ACTION = "SendSms";
    private static final String VERSION = "2019-07-11";

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final boolean debug;
    private final HttpClient httpClient;

    public SmsService(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debug = configManager.isDebug();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();
    }

    public boolean isValidPhoneNumber(String phone) {
        return PhoneUtil.isValidPhoneNumber(phone, configManager.getSmsPhoneRegex());
    }

    public static String normalizePhoneNumber(String phone) {
        return PhoneUtil.normalizePhoneNumber(phone);
    }

    public CompletableFuture<Boolean> sendVerificationCode(String phoneNumber, String countryCode, String code, String language) {
        if (!isValidPhoneNumber(phoneNumber)) {
            debugLog("Invalid phone number: " + maskPhone(phoneNumber));
            return CompletableFuture.completedFuture(false);
        }

        String secretId = configManager.getSmsSecretId();
        String secretKey = configManager.getSmsSecretKey();
        String sdkAppId = configManager.getSmsSdkAppId();
        String signName = configManager.getSmsSignName();
        String templateId = configManager.getSmsTemplateId();
        String region = configManager.getSmsRegion();

        if (secretId.isEmpty() || secretKey.isEmpty() || sdkAppId.isEmpty() || signName.isEmpty() || templateId.isEmpty()) {
            plugin.getLogger().warning("[VerifyMC] SMS configuration is incomplete. Please check sms.secret_id, sms.secret_key, sms.sdk_app_id, sms.sign_name, and sms.template_id in config.yml");
            return CompletableFuture.completedFuture(false);
        }

        try {
            String phoneNumberWithCountryCode = countryCode + phoneNumber;

            JSONObject body = new JSONObject();
            body.put("PhoneNumberSet", new JSONArray().put(phoneNumberWithCountryCode));
            body.put("SmsSdkAppId", sdkAppId);
            body.put("TemplateId", templateId);
            body.put("TemplateParamSet", new JSONArray().put(code));
            if (!signName.isEmpty()) {
                body.put("SignName", signName);
            }

            String requestBody = body.toString();
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String date = Instant.ofEpochSecond(Long.parseLong(timestamp))
                    .atZone(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            String authorization = buildAuthorization(secretId, secretKey, timestamp, date, requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + HOST))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Host", HOST)
                    .header("X-TC-Action", ACTION)
                    .header("X-TC-Version", VERSION)
                    .header("X-TC-Timestamp", timestamp)
                    .header("X-TC-Region", region)
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            debugLog("Sending SMS to " + maskPhone(phoneNumber) + " with template " + templateId);

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        JSONObject responseBody = new JSONObject(response.body());
                        JSONObject responseObj = responseBody.optJSONObject("Response");

                        if (responseObj != null) {
                            JSONObject error = responseObj.optJSONObject("Error");
                            if (error != null) {
                                String errorCode = error.optString("Code", "");
                                String errorMessage = error.optString("Message", "");
                                plugin.getLogger().warning("[VerifyMC] SMS API error: " + errorCode + " - " + errorMessage);
                                return false;
                            }

                            JSONArray sendStatusSet = responseObj.optJSONArray("SendStatusSet");
                            if (sendStatusSet != null && sendStatusSet.length() > 0) {
                                JSONObject sendStatus = sendStatusSet.getJSONObject(0);
                                String statusCode = sendStatus.optString("Code", "");
                                if ("Ok".equals(statusCode)) {
                                    debugLog("SMS sent successfully to " + maskPhone(phoneNumber));
                                    return true;
                                } else {
                                    String statusMessage = sendStatus.optString("Message", "");
                                    plugin.getLogger().warning("[VerifyMC] SMS send failed: " + statusCode + " - " + statusMessage);
                                    return false;
                                }
                            }
                        }

                        plugin.getLogger().warning("[VerifyMC] Unexpected SMS API response: " + response.body());
                        return false;
                    })
                    .exceptionally(e -> {
                        plugin.getLogger().warning("[VerifyMC] Failed to send SMS: " + e.getMessage());
                        debugLog("SMS send exception: " + e.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("[VerifyMC] Failed to send SMS: " + e.getMessage());
            debugLog("SMS send exception: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    private String buildAuthorization(String secretId, String secretKey, String timestamp, String date, String requestBody) throws Exception {
        String contentType = "application/json; charset=utf-8";
        String canonicalHeaders = "content-type:" + contentType + "\nhost:" + HOST + "\nx-tc-action:" + ACTION.toLowerCase() + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String hashedRequestPayload = sha256Hex(requestBody);

        String canonicalRequest = "POST" + "\n"
                + "/" + "\n"
                + "" + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + hashedRequestPayload;

        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign = "TC3-HMAC-SHA256" + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + hashedCanonicalRequest;

        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = hexEncode(hmacSha256(secretSigning, stringToSign));

        return "TC3-HMAC-SHA256" + " "
                + "Credential=" + secretId + "/" + credentialScope + ", "
                + "SignedHeaders=" + signedHeaders + ", "
                + "Signature=" + signature;
    }

    private static String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return hexEncode(hash);
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public void close() {
        // HttpClient.close() requires Java 21+; on Java 17 the JVM manages its lifecycle
    }

    private static String maskPhone(String phone) {
        return PhoneUtil.maskPhone(phone);
    }

    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] SmsService: " + msg);
        }
    }
}
