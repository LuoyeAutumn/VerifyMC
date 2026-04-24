package team.kitemc.verifymc.sms;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;
import team.kitemc.verifymc.core.ConfigManager;
import team.kitemc.verifymc.util.CryptoUtil;
import team.kitemc.verifymc.util.PhoneUtil;

public class TencentSmsProvider implements SmsProvider {
    private static final String SERVICE = "sms";
    private static final String HOST = "sms.tencentcloudapi.com";
    private static final String ACTION = "SendSms";
    private static final String VERSION = "2021-01-11";

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final boolean debug;
    private final HttpClient httpClient;
    private final int connectTimeout;
    private final int requestTimeout;

    public TencentSmsProvider(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debug = configManager.isDebug();
        this.connectTimeout = configManager.getSmsConnectTimeout();
        this.requestTimeout = configManager.getSmsRequestTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(connectTimeout))
                .build();
    }

    @Override
    public CompletableFuture<Boolean> sendVerificationCode(String phoneNumber, String code, String language) {
        String secretId = configManager.getSmsSecretId();
        String secretKey = configManager.getSmsSecretKey();
        String sdkAppId = configManager.getSmsSdkAppId();
        String signName = configManager.getSmsSignName();
        String templateId = configManager.getSmsTemplateId();
        String region = configManager.getSmsRegion();

        if (secretId.isEmpty() || secretKey.isEmpty() || sdkAppId.isEmpty() || signName.isEmpty() || templateId.isEmpty()) {
            plugin.getLogger().warning("[VerifyMC] SMS configuration is incomplete. Please check sms.tencent.secret_id, sms.tencent.secret_key, sms.sdk_app_id, sms.sign_name, and sms.template_id in config.yml");
            return CompletableFuture.completedFuture(false);
        }

        try {
            JSONObject body = new JSONObject();
            body.put("PhoneNumberSet", new JSONArray().put(phoneNumber));
            body.put("SmsSdkAppId", sdkAppId);
            body.put("TemplateId", templateId);
            body.put("TemplateParamSet", new JSONArray().put(code));
            body.put("SignName", signName);

            String requestBody = body.toString();
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String date = Instant.ofEpochSecond(Long.parseLong(timestamp))
                    .atZone(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            String authorization = buildAuthorization(secretId, secretKey, timestamp, date, requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + HOST))
                    .timeout(java.time.Duration.ofSeconds(requestTimeout))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Host", HOST)
                    .header("X-TC-Action", ACTION)
                    .header("X-TC-Version", VERSION)
                    .header("X-TC-Timestamp", timestamp)
                    .header("X-TC-Region", region)
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            debugLog("Sending SMS to " + PhoneUtil.maskPhone(phoneNumber) + " with template " + templateId);

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        JSONObject responseBody = new JSONObject(response.body());
                        JSONObject responseObj = responseBody.optJSONObject("Response");

                        if (responseObj != null) {
                            JSONObject error = responseObj.optJSONObject("Error");
                            if (error != null) {
                                plugin.getLogger().warning("[VerifyMC] SMS API error: "
                                        + error.optString("Code", "") + " - " + error.optString("Message", ""));
                                return false;
                            }

                            JSONArray sendStatusSet = responseObj.optJSONArray("SendStatusSet");
                            if (sendStatusSet != null && !sendStatusSet.isEmpty()) {
                                JSONObject sendStatus = sendStatusSet.getJSONObject(0);
                                if ("Success".equals(sendStatus.optString("Code", ""))) {
                                    debugLog("SMS sent successfully to " + PhoneUtil.maskPhone(phoneNumber));
                                    return true;
                                }
                                plugin.getLogger().warning("[VerifyMC] SMS send failed: "
                                        + sendStatus.optString("Code", "") + " - " + sendStatus.optString("Message", ""));
                                return false;
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

    @Override
    public String getProviderName() {
        return "tencent";
    }

    private String buildAuthorization(String secretId, String secretKey, String timestamp, String date, String requestBody) throws Exception {
        String contentType = "application/json; charset=utf-8";
        String canonicalHeaders = "content-type:" + contentType + "\nhost:" + HOST + "\nx-tc-action:" + ACTION.toLowerCase() + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String hashedRequestPayload = CryptoUtil.sha256Hex(requestBody);

        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedRequestPayload;
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + CryptoUtil.sha256Hex(canonicalRequest);

        byte[] secretDate = CryptoUtil.hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = CryptoUtil.hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = CryptoUtil.hmacSha256(secretService, "tc3_request");
        String signature = CryptoUtil.hexEncode(CryptoUtil.hmacSha256(secretSigning, stringToSign));

        return "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private String getDefaultCountryCode() {
        var codes = configManager.getCountryCodes();
        return codes.isEmpty() ? "+86" : codes.get(0);
    }

    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] TencentSmsProvider: " + msg);
        }
    }
}
