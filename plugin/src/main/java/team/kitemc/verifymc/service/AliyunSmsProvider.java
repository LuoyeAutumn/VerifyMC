package team.kitemc.verifymc.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;
import org.json.JSONObject;
import team.kitemc.verifymc.core.ConfigManager;
import team.kitemc.verifymc.util.CryptoUtil;
import team.kitemc.verifymc.util.PhoneUtil;

public class AliyunSmsProvider implements SmsProvider {

    private static final String HOST = "dysmsapi.aliyuncs.com";
    private static final String ACTION = "SendSms";
    private static final String VERSION = "2017-05-25";

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final boolean debug;
    private final HttpClient httpClient;

    public AliyunSmsProvider(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debug = configManager.isDebug();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();
    }

    @Override
    public CompletableFuture<Boolean> sendVerificationCode(String phoneNumber, String countryCode, String code, String language) {
        String accessKeyId = configManager.getSmsAccessKeyId();
        String accessKeySecret = configManager.getSmsAccessKeySecret();
        String signName = configManager.getSmsSignName();
        String templateCode = configManager.getSmsTemplateId();

        if (accessKeyId.isEmpty() || accessKeySecret.isEmpty() || signName.isEmpty() || templateCode.isEmpty()) {
            plugin.getLogger().warning("[VerifyMC] Aliyun SMS configuration is incomplete. Please check sms.access_key_id, sms.access_key_secret, sms.sign_name, and sms.template_id in config.yml");
            return CompletableFuture.completedFuture(false);
        }

        try {
            String timestamp = Instant.now().atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
            String signatureNonce = UUID.randomUUID().toString();

            String fullPhone = countryCode + phoneNumber;
            String requestBody = "PhoneNumbers=" + CryptoUtil.percentEncode(fullPhone)
                + "&SignName=" + CryptoUtil.percentEncode(signName)
                + "&TemplateCode=" + CryptoUtil.percentEncode(templateCode)
                + "&TemplateParam=" + CryptoUtil.percentEncode(new JSONObject().put("code", code).toString());

            String hashedPayload = CryptoUtil.sha256Hex(requestBody);

            String contentType = "application/x-www-form-urlencoded";
            String canonicalHeaders = "content-type:" + contentType + "\n"
                + "host:" + HOST + "\n"
                + "x-acs-action:" + ACTION + "\n"
                + "x-acs-content-sha256:" + hashedPayload + "\n"
                + "x-acs-date:" + timestamp + "\n"
                + "x-acs-signature-nonce:" + signatureNonce + "\n"
                + "x-acs-version:" + VERSION + "\n";
            String signedHeaders = "content-type;host;x-acs-action;x-acs-content-sha256;x-acs-date;x-acs-signature-nonce;x-acs-version";

            String canonicalRequest = "POST" + "\n"
                + "/" + "\n"
                + "" + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + hashedPayload;

            String stringToSign = "ACS3-HMAC-SHA256" + "\n" + CryptoUtil.sha256Hex(canonicalRequest);

            String signature = CryptoUtil.hexEncode(CryptoUtil.hmacSha256(
                accessKeySecret.getBytes(StandardCharsets.UTF_8), stringToSign));

            String authorization = "ACS3-HMAC-SHA256 Credential=" + accessKeyId
                + ",SignedHeaders=" + signedHeaders
                + ",Signature=" + signature;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + HOST + "/"))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Content-Type", contentType)
                .header("Host", HOST)
                .header("x-acs-action", ACTION)
                .header("x-acs-version", VERSION)
                .header("x-acs-date", timestamp)
                .header("x-acs-signature-nonce", signatureNonce)
                .header("x-acs-content-sha256", hashedPayload)
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            debugLog("Sending SMS to " + PhoneUtil.maskPhone(phoneNumber) + " via Aliyun");

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JSONObject json = new JSONObject(response.body());
                    String respCode = json.optString("Code", "");
                    if ("OK".equals(respCode)) {
                        debugLog("SMS sent successfully to " + PhoneUtil.maskPhone(phoneNumber) + " via Aliyun");
                        return true;
                    } else {
                        String message = json.optString("Message", "");
                        plugin.getLogger().warning("[VerifyMC] Aliyun SMS API error: " + respCode + " - " + message);
                        return false;
                    }
                })
                .exceptionally(e -> {
                    plugin.getLogger().warning("[VerifyMC] Failed to send SMS via Aliyun: " + e.getMessage());
                    debugLog("Aliyun SMS send exception: " + e.getMessage());
                    return false;
                });
        } catch (Exception e) {
            plugin.getLogger().warning("[VerifyMC] Failed to send SMS via Aliyun: " + e.getMessage());
            debugLog("Aliyun SMS send exception: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (NoSuchMethodError ignored) {
        }
    }

    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] AliyunSmsProvider: " + msg);
        }
    }
}
