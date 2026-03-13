package com.daodun.voice.volcano;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * 火山引擎 SAMI 在线服务鉴权：使用 access_key + secret_key 向 open.volcengineapi.com 获取临时 token，
 * 再以该 token 调用 sami.bytedance.com（如 TTS invoke）。参考文档：服务鉴权-获取Token。
 */
@Slf4j
public class SamiTokenProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HOST = "open.volcengineapi.com";
    private static final String SERVICE = "sami";
    private static final String REGION = "cn-north-1";
    private static final String ACTION = "GetToken";
    private static final String VERSION = "2021-07-27";
    private static final String TOKEN_VERSION = "volc-auth-v1";
    private static final int TOKEN_EXPIRATION_SEC = 3600;
    private static final int REFRESH_BUFFER_SEC = 300;

    private final String accessKey;
    private final String secretKey;
    private final String appKey;

    private volatile String cachedToken;
    private volatile long cachedExpiresAt;

    public SamiTokenProvider(String accessKey, String secretKey, String appKey) {
        this.accessKey = accessKey == null ? "" : accessKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.appKey = appKey == null ? "" : appKey.trim();
    }

    public boolean isConfigured() {
        return !accessKey.isBlank() && !secretKey.isBlank() && !appKey.isBlank();
    }

    /**
     * 获取用于调用 sami.bytedance.com 的鉴权 token，带缓存，临近过期前刷新。
     */
    public String getToken() throws Exception {
        long now = System.currentTimeMillis() / 1000;
        if (cachedToken != null && cachedExpiresAt > now + REFRESH_BUFFER_SEC) {
            return cachedToken;
        }
        synchronized (this) {
            now = System.currentTimeMillis() / 1000;
            if (cachedToken != null && cachedExpiresAt > now + REFRESH_BUFFER_SEC) {
                return cachedToken;
            }
            String token = fetchToken();
            cachedToken = token;
            cachedExpiresAt = now + TOKEN_EXPIRATION_SEC;
            return token;
        }
    }

    private String fetchToken() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "token_version", TOKEN_VERSION,
                "appkey", appKey,
                "expiration", TOKEN_EXPIRATION_SEC
        ));

        TreeMap<String, String> query = new TreeMap<>();
        query.put("Action", ACTION);
        query.put("Version", VERSION);

        String xDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        String shortDate = xDate.substring(0, 8);

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] bodyHash = sha256.digest(body.getBytes(StandardCharsets.UTF_8));
        String xContentSha256 = bytesToHex(bodyHash);

        String canonicalQuery = buildCanonicalQuery(query);
        String signedHeaders = "content-type;host;x-content-sha256;x-date";
        String canonicalRequest = "POST" + "\n"
                + "/" + "\n"
                + canonicalQuery + "\n"
                + "content-type:application/json\n"
                + "host:" + HOST + "\n"
                + "x-content-sha256:" + xContentSha256 + "\n"
                + "x-date:" + xDate + "\n"
                + "\n"
                + signedHeaders + "\n"
                + xContentSha256;

        byte[] hashedCanonical = sha256.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        String credentialScope = shortDate + "/" + REGION + "/" + SERVICE + "/request";
        String stringToSign = "HMAC-SHA256" + "\n" + xDate + "\n" + credentialScope + "\n" + bytesToHex(hashedCanonical);

        byte[] kDate = hmacSha256(secretKey.getBytes(StandardCharsets.UTF_8), shortDate);
        byte[] kRegion = hmacSha256(kDate, REGION);
        byte[] kService = hmacSha256(kRegion, SERVICE);
        byte[] kSigning = hmacSha256(kService, "request");
        String signature = bytesToHex(hmacSha256(kSigning, stringToSign));

        String authorization = "HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        String url = "https://" + HOST + "/?" + canonicalQuery;
        // 不设置 Host：Java HttpClient 禁止手动设置 Host（restricted header），会由 URI 自动带上
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-Date", xDate)
                .header("X-Content-Sha256", xContentSha256)
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String responseBody = response.body();

        if (response.statusCode() >= 400) {
            log.warn("[SamiToken] GetToken 失败 status={} body={}", response.statusCode(), truncate(responseBody, 500));
            throw new IllegalStateException("GetToken 失败: HTTP " + response.statusCode() + " " + responseBody);
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (Exception e) {
            log.warn("[SamiToken] GetToken 响应非 JSON body={}", truncate(responseBody, 500));
            throw new IllegalStateException("GetToken 响应解析失败: " + e.getMessage());
        }

        // 火山 OpenAPI 错误时返回 ResponseMetadata.Error，无 status_code
        JsonNode meta = root.path("ResponseMetadata");
        if (!meta.isMissingNode()) {
            JsonNode err = meta.path("Error");
            if (!err.isMissingNode()) {
                String code = err.path("Code").asText("");
                String message = err.path("Message").asText("");
                log.warn("[SamiToken] GetToken 鉴权/请求错误 Code={} Message={} 请确认 access_key/secret_key 来自「访问控制-密钥管理」", code, message);
                throw new IllegalStateException("GetToken 失败: " + code + " " + message);
            }
        }

        int statusCode = root.path("status_code").asInt(0);
        if (statusCode != 20000000) {
            String statusText = root.path("status_text").asText("");
            log.warn("[SamiToken] GetToken 业务失败 status_code={} status_text={} 完整响应={}", statusCode, statusText, truncate(responseBody, 800));
            throw new IllegalStateException("GetToken 失败: " + statusCode + " " + statusText);
        }

        String token = root.path("token").asText(null);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GetToken 返回无 token");
        }
        log.info("[SamiToken] 获取 token 成功 expires_in={}s", TOKEN_EXPIRATION_SEC);
        return token;
    }

    private static String buildCanonicalQuery(TreeMap<String, String> query) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(encode(e.getKey())).append("=").append(encode(e.getValue()));
        }
        return sb.toString();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
