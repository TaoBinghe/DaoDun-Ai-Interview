package com.daodun.service.impl;

import com.daodun.config.VoiceProperties;
import com.daodun.dto.voice.TtsResult;
import com.daodun.service.VoiceSynthesisService;
import com.daodun.voice.volcano.OpenspeechLongTextTtsClient;
import com.daodun.voice.volcano.OpenspeechWebsocketTtsClient;
import com.daodun.voice.volcano.SamiTokenProvider;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceSynthesisServiceImpl implements VoiceSynthesisService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VoiceProperties voiceProperties;
    private volatile SamiTokenProvider samiTokenProvider;

    @Override
    public TtsResult synthesize(String text) {
        if (text == null || text.isBlank()) {
            return TtsResult.builder().subtitle("").mimeType("audio/wav").build();
        }

        VoiceProperties.Tts tts = voiceProperties.getTts();
        if (tts.isMockEnabled()) {
            return TtsResult.builder()
                    .subtitle(text)
                    .mimeType(toMimeType(tts.getFormat()))
                    .audioBase64(null)
                    .build();
        }
        if (safe(tts.getEndpoint()).isBlank()) {
            log.warn("[Voice][PureTTS] TTS endpoint 未配置，降级为字幕输出");
            return TtsResult.builder().subtitle(text).mimeType(toMimeType(tts.getFormat())).build();
        }

        String endpoint = safe(tts.getEndpoint()).toLowerCase();
        if (endpoint.startsWith("wss://") && endpoint.contains("/tts/ws_binary")) {
            return synthesizeOpenspeechWebsocket(tts, text);
        }
        if (endpoint.contains("openspeech.bytedance.com") && endpoint.contains("/tts_async")) {
            return synthesizeOpenspeechLongText(tts, text);
        }

        try {
            String token = resolveToken(tts);
            if (token == null || token.isBlank()) {
                log.warn("[Voice][PureTTS] 未配置 accessKey/secretKey 且 apiKey 易导致 401 IllegalToken，请配置 voice.tts.accessKey 与 voice.tts.secretKey");
            }

            Map<String, Object> payload = Map.of(
                    "speaker", safe(tts.getSpeaker()),
                    "text", text,
                    "audio_config", Map.of(
                            "format", safe(tts.getFormat()),
                            "sample_rate", tts.getSampleRate(),
                            "enable_timestamp", tts.isEnableTimestamp()
                    )
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("appkey", safe(tts.getAppKey()));
            body.put("token", token != null ? token : safe(tts.getApiKey()));
            body.put("namespace", safe(tts.getNamespace()));
            body.put("payload", MAPPER.writeValueAsString(payload));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(safe(tts.getEndpoint()) + "?version=" + safe(tts.getVersion())))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            log.info("[Voice][PureTTS] 请求纯TTS textLength={} speaker={} format={} endpoint={}",
                    text.length(), safe(tts.getSpeaker()), safe(tts.getFormat()), safe(tts.getEndpoint()));

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String audioBase64 = extractAudio(response.body());
            boolean success = response.statusCode() < 400 && audioBase64 != null && !audioBase64.isBlank();

            if (!success) {
                log.warn("[Voice][PureTTS] 合成失败 status={} response={}",
                        response.statusCode(), truncate(response.body(), 800));
                return TtsResult.builder()
                        .subtitle(text)
                        .mimeType(toMimeType(tts.getFormat()))
                        .audioBase64(null)
                        .build();
            }

            return TtsResult.builder()
                    .subtitle(text)
                    .audioBase64(audioBase64)
                    .mimeType(toMimeType(tts.getFormat()))
                    .build();
        } catch (Exception e) {
            log.warn("[Voice][PureTTS] 合成异常，降级字幕: {} | endpoint={} apiKeyPresent={} appKeyPresent={} resourceId={}",
                    e.getMessage(),
                    safe(tts.getEndpoint()),
                    !safe(tts.getApiKey()).isBlank(),
                    !safe(tts.getAppKey()).isBlank(),
                    safe(tts.getResourceId()));
            return TtsResult.builder()
                    .subtitle(text)
                    .mimeType(toMimeType(tts.getFormat()))
                    .audioBase64(null)
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

    /**
     * 豆包语音 - 精品长文本语音合成（openspeech.bytedance.com）。
     * 鉴权：Resource-Id + Authorization: Bearer; {Access Token}，见 https://www.volcengine.com/docs/6561/1105162
     */
    private TtsResult synthesizeOpenspeechLongText(VoiceProperties.Tts tts, String text) {
        String accessToken = safe(tts.getApiKey());
        String appIdVal = safe(tts.getAppId());
        if (appIdVal.isBlank()) {
            appIdVal = safe(tts.getAppKey());
        }
        if (accessToken.isBlank() || appIdVal.isBlank()) {
            log.warn("[Voice][OpenspeechLongTTS] 未配置 apiKey(Access Token) 或 appId/appKey");
            return TtsResult.builder().subtitle(text).mimeType(toMimeType(tts.getFormat())).audioBase64(null).build();
        }
        try {
            OpenspeechLongTextTtsClient client = new OpenspeechLongTextTtsClient(
                    accessToken, appIdVal, safe(tts.getVoiceType()), safe(tts.getFormat()), tts.getSampleRate());
            byte[] audioBytes = client.synthesize(text);
            if (audioBytes == null || audioBytes.length == 0) {
                return TtsResult.builder().subtitle(text).mimeType(toMimeType(tts.getFormat())).audioBase64(null).build();
            }
            String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
            return TtsResult.builder()
                    .subtitle(text)
                    .audioBase64(audioBase64)
                    .mimeType(toMimeType(tts.getFormat()))
                    .build();
        } catch (Exception e) {
            log.warn("[Voice][OpenspeechLongTTS] 合成异常: {}", e.getMessage());
            return TtsResult.builder().subtitle(text).mimeType(toMimeType(tts.getFormat())).audioBase64(null).build();
        }
    }

    /**
     * 豆包语音 - 普通版语音合成（WebSocket 二进制协议）。
     * 文档：wss://openspeech.bytedance.com/api/v1/tts/ws_binary
     * 鉴权：Authorization: Bearer; {token}，请求体包含 appid / cluster。
     */
    private TtsResult synthesizeOpenspeechWebsocket(VoiceProperties.Tts tts, String text) {
        String accessToken = safe(tts.getApiKey());
        String appIdVal = safe(tts.getAppId());
        if (appIdVal.isBlank()) {
            appIdVal = safe(tts.getAppKey());
        }
        if (accessToken.isBlank() || appIdVal.isBlank()) {
            log.warn("[Voice][OpenspeechWsTTS] 未配置 apiKey(Access Token) 或 appId/appKey");
            return TtsResult.builder().subtitle(text).mimeType(toMimeType(tts.getFormat())).audioBase64(null).build();
        }
        try {
            OpenspeechWebsocketTtsClient client = new OpenspeechWebsocketTtsClient(
                    safe(tts.getEndpoint()),
                    accessToken,
                    appIdVal,
                    safe(tts.getCluster()),
                    safe(tts.getVoiceType()),
                    safe(tts.getFormat()),
                    tts.getSampleRate()
            );
            byte[] audioBytes = client.synthesize(text);
            if (audioBytes == null || audioBytes.length == 0) {
                return TtsResult.builder().subtitle(text).mimeType(toMimeType(tts.getFormat())).audioBase64(null).build();
            }
            return TtsResult.builder()
                    .subtitle(text)
                    .audioBase64(Base64.getEncoder().encodeToString(audioBytes))
                    .mimeType(toMimeType(tts.getFormat()))
                    .build();
        } catch (Exception e) {
            log.warn("[Voice][OpenspeechWsTTS] 合成异常: {}", e.getMessage());
            return TtsResult.builder().subtitle(text).mimeType(toMimeType(tts.getFormat())).audioBase64(null).build();
        }
    }

    /** 优先用 accessKey/secretKey 获取临时 token；未配置时退回 apiKey（易 401）。 */
    private String resolveToken(VoiceProperties.Tts tts) {
        if (samiTokenProvider == null) {
            synchronized (this) {
                if (samiTokenProvider == null && !safe(tts.getAccessKey()).isBlank() && !safe(tts.getSecretKey()).isBlank()) {
                    samiTokenProvider = new SamiTokenProvider(tts.getAccessKey(), tts.getSecretKey(), safe(tts.getAppKey()));
                }
            }
        }
        if (samiTokenProvider != null && samiTokenProvider.isConfigured()) {
            try {
                return samiTokenProvider.getToken();
            } catch (Exception e) {
                log.warn("[Voice][PureTTS] GetToken 失败，降级使用 apiKey: {}", e.getMessage());
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
