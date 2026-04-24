package team.kitemc.verifymc.sms;

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
    private final int connectTimeout;
    private final int requestTimeout;

    public AliyunSmsProvider(Plugin plugin, ConfigManager configManager) {
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
        String accessKeyId = configManager.getSmsAccessKeyId();
        String accessKeySecret = configManager.getSmsAccessKeySecret();
        String signName = configManager.getSmsSignName();
        String templateCode = configManager.getSmsTemplateId();

        if (accessKeyId.isEmpty() || accessKeySecret.isEmpty() || signName.isEmpty() || templateCode.isEmpty()) {
            plugin.getLogger().warning("[VerifyMC] Aliyun SMS configuration is incomplete. Please check sms.aliyun.access_key_id, sms.aliyun.access_key_secret, sms.sign_name, and sms.template_id in config.yml");
            return CompletableFuture.completedFuture(false);
        }

        try {
            String timestamp = Instant.now().atZone(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
            String signatureNonce = UUID.randomUUID().toString();
            String queryString = CryptoUtil.percentEncode("PhoneNumbers") + "=" + CryptoUtil.percentEncode(phoneNumber)
                    + "&" + CryptoUtil.percentEncode("SignName") + "=" + CryptoUtil.percentEncode(signName)
                    + "&" + CryptoUtil.percentEncode("TemplateCode") + "=" + CryptoUtil.percentEncode(templateCode)
                    + "&" + CryptoUtil.percentEncode("TemplateParam") + "=" + CryptoUtil.percentEncode(new JSONObject().put("code", code).toString());

            String hashedPayload = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            String canonicalHeaders = "host:" + HOST + "\n"
                    + "x-acs-action:" + ACTION + "\n"
                    + "x-acs-content-sha256:" + hashedPayload + "\n"
                    + "x-acs-date:" + timestamp + "\n"
                    + "x-acs-signature-nonce:" + signatureNonce + "\n"
                    + "x-acs-version:" + VERSION + "\n";
            String signedHeaders = "host;x-acs-action;x-acs-content-sha256;x-acs-date;x-acs-signature-nonce;x-acs-version";
            String canonicalRequest = "POST\n/\n" + queryString + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedPayload;
            String stringToSign = "ACS3-HMAC-SHA256\n" + CryptoUtil.sha256Hex(canonicalRequest);
            String signature = CryptoUtil.hexEncode(CryptoUtil.hmacSha256(
                    accessKeySecret.getBytes(StandardCharsets.UTF_8), stringToSign));
            String authorization = "ACS3-HMAC-SHA256 Credential=" + accessKeyId
                    + ",SignedHeaders=" + signedHeaders
                    + ",Signature=" + signature;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + HOST + "/?" + queryString))
                    .timeout(java.time.Duration.ofSeconds(requestTimeout))
                    .header("x-acs-action", ACTION)
                    .header("x-acs-version", VERSION)
                    .header("x-acs-date", timestamp)
                    .header("x-acs-signature-nonce", signatureNonce)
                    .header("x-acs-content-sha256", hashedPayload)
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(""))
                    .build();

            debugLog("Sending SMS to " + PhoneUtil.maskPhone(phoneNumber) + " via Aliyun");

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        JSONObject json = new JSONObject(response.body());
                        if ("OK".equals(json.optString("Code", ""))) {
                            debugLog("SMS sent successfully to " + PhoneUtil.maskPhone(phoneNumber) + " via Aliyun");
                            return true;
                        }
                        plugin.getLogger().warning("[VerifyMC] Aliyun SMS API error: "
                                + json.optString("Code", "") + " - " + json.optString("Message", ""));
                        return false;
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
    public String getProviderName() {
        return "aliyun";
    }

    private String getDefaultCountryCode() {
        var codes = configManager.getCountryCodes();
        return codes.isEmpty() ? "+86" : codes.get(0);
    }

    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] AliyunSmsProvider: " + msg);
        }
    }
}
