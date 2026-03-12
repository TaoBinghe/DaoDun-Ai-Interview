package com.daodun.service.impl;

import com.daodun.config.VoiceProperties;
import com.daodun.dto.voice.VoiceRealtimeDiagnosticResponse;
import com.daodun.dto.voice.VoiceTtsDiagnosticResponse;
import com.daodun.service.VoiceDiagnosticService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceDiagnosticServiceImpl implements VoiceDiagnosticService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VoiceProperties voiceProperties;

    @Override
    public VoiceTtsDiagnosticResponse diagnoseTts(String text) {
        VoiceProperties.Tts tts = voiceProperties.getTts();
        VoiceProperties.Stt stt = voiceProperties.getStt();
        String content = (text == null || text.isBlank()) ? "你好，我是 AI 面试官，这是一条 TTS 诊断语音。" : text.trim();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("endpoint", safe(tts.getEndpoint()));
        config.put("version", safe(tts.getVersion()));
        config.put("namespace", safe(tts.getNamespace()));
        config.put("resourceId", safe(tts.getResourceId()));
        config.put("speaker", safe(tts.getSpeaker()));
        config.put("format", safe(tts.getFormat()));
        config.put("sampleRate", tts.getSampleRate());
        config.put("enableTimestamp", tts.isEnableTimestamp());
        config.put("ttsApiKeyPresent", !safe(tts.getApiKey()).isBlank());
        config.put("ttsApiKeyMasked", mask(tts.getApiKey()));
        config.put("ttsAppKeyPresent", !safe(tts.getAppKey()).isBlank());
        config.put("ttsAppKeyMasked", mask(tts.getAppKey()));
        config.put("sttApiKeyPresent", !safe(stt.getApiKey()).isBlank());
        config.put("sttApiKeyMasked", mask(stt.getApiKey()));
        config.put("sttAppIdPresent", !safe(stt.getAppId()).isBlank());
        config.put("sttAppIdMasked", mask(stt.getAppId()));

        if (safe(tts.getEndpoint()).isBlank()) {
            return VoiceTtsDiagnosticResponse.builder()
                    .config(config)
                    .success(false)
                    .errorMessage("voice.tts.endpoint 为空")
                    .build();
        }

        try {
            Map<String, Object> payload = Map.of(
                    "speaker", safe(tts.getSpeaker()),
                    "text", content,
                    "audio_config", Map.of(
                            "format", safe(tts.getFormat()),
                            "sample_rate", tts.getSampleRate(),
                            "enable_timestamp", tts.isEnableTimestamp()
                    )
            );

            Map<String, Object> body = Map.of(
                    "appkey", safe(tts.getAppKey()),
                    "token", safe(tts.getApiKey()),
                    "namespace", safe(tts.getNamespace()),
                    "payload", MAPPER.writeValueAsString(payload)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tts.getEndpoint() + "?version=" + safe(tts.getVersion())))
                    .header("Authorization", "Bearer " + safe(tts.getApiKey()))
                    .header("Resource-Id", safe(tts.getResourceId()))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String audio = extractAudio(response.body());
            boolean ok = response.statusCode() < 400 && audio != null && !audio.isBlank();

            return VoiceTtsDiagnosticResponse.builder()
                    .config(config)
                    .httpStatus(response.statusCode())
                    .success(ok)
                    .audioBase64Length(audio != null ? audio.length() : 0)
                    .mimeType(toMimeType(tts.getFormat()))
                    .responseSnippet(truncate(response.body(), 800))
                    .errorMessage(ok ? null : "TTS 调用失败或未返回音频")
                    .build();
        } catch (Exception e) {
            log.warn("[VoiceDiagnostic] TTS 诊断失败: {}", e.getMessage());
            return VoiceTtsDiagnosticResponse.builder()
                    .config(config)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public VoiceRealtimeDiagnosticResponse diagnoseRealtimeVoice(String text) {
        Map<String, Object> config = new LinkedHashMap<>();
        String speaker = "";
        try {
            VoiceProperties.Tts tts = voiceProperties.getTts();
            VoiceProperties.Stt stt = voiceProperties.getStt();
            String content = (text == null || text.isBlank()) ? "你好，这是端到端语音诊断。" : text.trim();
            speaker = safe(tts.getSpeaker());

            config.put("sttEndpoint", safe(stt.getEndpoint()));
            config.put("speaker", speaker);
            config.put("sttApiKeyPresent", !safe(stt.getApiKey()).isBlank());
            config.put("sttAppIdPresent", !safe(stt.getAppId()).isBlank());
            config.put("sttAppKeyPresent", !safe(stt.getAppKey()).isBlank());
            config.put("sttResourceIdPresent", !safe(stt.getResourceId()).isBlank());

            if (safe(stt.getEndpoint()).isBlank()) {
                return VoiceRealtimeDiagnosticResponse.builder()
                        .config(config)
                        .success(false)
                        .speakerUsed(speaker)
                        .errorMessage("voice.stt.endpoint 为空，端到端语音依赖 STT WebSocket 地址")
                        .build();
            }

            URI uri = URI.create(stt.getEndpoint().trim());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Api-App-ID", safe(stt.getAppId()));
            headers.put("X-Api-Access-Key", safe(stt.getApiKey()));
            headers.put("X-Api-Resource-Id", safe(stt.getResourceId()));
            headers.put("X-Api-App-Key", safe(stt.getAppKey()));

            VolcanoRealtimeVoiceOutputClient client = new VolcanoRealtimeVoiceOutputClient(uri, headers);
            byte[] wavBytes = client.synthesize(content, speaker);

            boolean ok = wavBytes != null && wavBytes.length > 0;
            return VoiceRealtimeDiagnosticResponse.builder()
                    .config(config)
                    .success(ok)
                    .speakerUsed(speaker)
                    .audioBytesLength(wavBytes != null ? wavBytes.length : 0)
                    .errorMessage(ok ? null : "未收到音频数据")
                    .build();
        } catch (Throwable e) {
            log.warn("[VoiceDiagnostic] 端到端语音诊断失败: {}", e.getMessage(), e);
            String msg = e.getMessage() != null ? e.getMessage() : (e.getClass().getName() + " (无 message)");
            return VoiceRealtimeDiagnosticResponse.builder()
                    .config(config.isEmpty() ? Map.of("error", "配置构建前已异常") : config)
                    .success(false)
                    .speakerUsed(speaker)
                    .errorMessage(msg)
                    .build();
        }
    }

    private String extractAudio(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
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
        } catch (Exception e) {
            return null;
        }
    }

    private String toMimeType(String format) {
        if ("wav".equalsIgnoreCase(format)) return "audio/wav";
        if ("ogg_opus".equalsIgnoreCase(format)) return "audio/ogg";
        return "audio/mpeg";
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String mask(String value) {
        String safeValue = safe(value);
        if (safeValue.isBlank()) {
            return "<empty>";
        }
        if (safeValue.length() <= 8) {
            return "***" + safeValue.charAt(safeValue.length() - 1);
        }
        return safeValue.substring(0, 4) + "***" + safeValue.substring(safeValue.length() - 4);
    }
}
