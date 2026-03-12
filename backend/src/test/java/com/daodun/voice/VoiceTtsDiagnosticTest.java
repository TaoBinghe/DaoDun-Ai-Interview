package com.daodun.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceTtsDiagnosticTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("手动诊断火山 TTS 环境变量与鉴权")
    void diagnoseVolcanoTtsConfig() throws Exception {
        TtsConfig cfg = TtsConfig.fromEnv();

        System.out.println("[VoiceTtsDiagnostic] endpoint=" + cfg.endpoint);
        System.out.println("[VoiceTtsDiagnostic] version=" + cfg.version);
        System.out.println("[VoiceTtsDiagnostic] namespace=" + cfg.namespace);
        System.out.println("[VoiceTtsDiagnostic] resourceId=" + cfg.resourceId);
        System.out.println("[VoiceTtsDiagnostic] speaker=" + cfg.speaker);
        System.out.println("[VoiceTtsDiagnostic] appKey=" + mask(cfg.appKey) + " source=" + cfg.appKeySource);
        System.out.println("[VoiceTtsDiagnostic] apiKey=" + mask(cfg.apiKey) + " source=" + cfg.apiKeySource);

        assertFalse(cfg.appKey.isBlank(), "TTS appKey 为空，请配置 VOLCANO_TTS_APP_KEY 或 VOLCANO_STT_APP_ID");
        assertFalse(cfg.apiKey.isBlank(), "TTS apiKey 为空，请配置 VOLCANO_TTS_API_KEY 或 VOLCANO_STT_API_KEY");

        Map<String, Object> payload = Map.of(
                "speaker", cfg.speaker,
                "text", "你好，我是 AI 面试官，这是一条 TTS 诊断语音。",
                "audio_config", Map.of(
                        "format", cfg.format,
                        "sample_rate", cfg.sampleRate,
                        "enable_timestamp", cfg.enableTimestamp
                )
        );

        Map<String, Object> body = Map.of(
                "appkey", cfg.appKey,
                "token", cfg.apiKey,
                "namespace", cfg.namespace,
                "payload", MAPPER.writeValueAsString(payload)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cfg.endpoint + "?version=" + cfg.version))
                .header("Authorization", "Bearer " + cfg.apiKey)
                .header("Resource-Id", cfg.resourceId)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("[VoiceTtsDiagnostic] status=" + response.statusCode());
        System.out.println("[VoiceTtsDiagnostic] body=" + truncate(response.body(), 800));

        assertTrue(response.statusCode() < 400, "TTS HTTP 调用失败，请检查日志中的 status/body");

        JsonNode root = MAPPER.readTree(response.body());
        String audio = extractAudio(root);
        assertFalse(audio == null || audio.isBlank(), "TTS 返回成功但没有拿到音频数据，请检查 body");
    }

    private static String extractAudio(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        JsonNode data = root.path("data");
        if (data.isTextual()) {
            return data.asText();
        }
        JsonNode audio = data.path("audio");
        if (audio.isTextual()) {
            return audio.asText();
        }
        JsonNode audioBase64 = root.path("audioBase64");
        return audioBase64.isTextual() ? audioBase64.asText() : null;
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        if (value.length() <= 8) {
            return "***" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    private record TtsConfig(
            String endpoint,
            String apiKey,
            String apiKeySource,
            String appKey,
            String appKeySource,
            String resourceId,
            String version,
            String namespace,
            String speaker,
            String format,
            int sampleRate,
            boolean enableTimestamp
    ) {
        static TtsConfig fromEnv() {
            String directApiKey = env("VOLCANO_TTS_API_KEY");
            String fallbackApiKey = env("VOLCANO_STT_API_KEY");
            String directAppKey = env("VOLCANO_TTS_APP_KEY");
            String fallbackAppKey = env("VOLCANO_STT_APP_ID");

            String apiKey = !directApiKey.isBlank() ? directApiKey : fallbackApiKey;
            String apiKeySource = !directApiKey.isBlank() ? "VOLCANO_TTS_API_KEY" : "VOLCANO_STT_API_KEY";

            String appKey = !directAppKey.isBlank() ? directAppKey : fallbackAppKey;
            String appKeySource = !directAppKey.isBlank() ? "VOLCANO_TTS_APP_KEY" : "VOLCANO_STT_APP_ID";

            return new TtsConfig(
                    envOrDefault("VOLCANO_TTS_ENDPOINT", "https://sami.bytedance.com/api/v1/invoke"),
                    apiKey,
                    apiKeySource,
                    appKey,
                    appKeySource,
                    envOrDefault("VOLCANO_TTS_RESOURCE_ID", "volc.service_type.10029"),
                    envOrDefault("VOLCANO_TTS_VERSION", "v4"),
                    envOrDefault("VOLCANO_TTS_NAMESPACE", "TTS"),
                    envOrDefault("VOLCANO_TTS_SPEAKER", "zh_female_qingxin"),
                    envOrDefault("VOLCANO_TTS_FORMAT", "mp3"),
                    parseInt(envOrDefault("VOLCANO_TTS_SAMPLE_RATE", "24000"), 24000),
                    parseBoolean(envOrDefault("VOLCANO_TTS_ENABLE_TIMESTAMP", "true"))
            );
        }

        private static String env(String key) {
            String value = System.getenv(key);
            return value == null ? "" : value.trim();
        }

        private static String envOrDefault(String key, String defaultValue) {
            String value = env(key);
            return value.isBlank() ? defaultValue : value;
        }

        private static int parseInt(String value, int defaultValue) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                return defaultValue;
            }
        }

        private static boolean parseBoolean(String value) {
            return !"false".equalsIgnoreCase(value);
        }
    }
}
