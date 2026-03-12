package com.daodun.service.impl;

import com.daodun.common.BusinessException;
import com.daodun.config.VoiceProperties;
import com.daodun.service.VoiceRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceRecognitionServiceImpl implements VoiceRecognitionService {

    private final VoiceProperties voiceProperties;

    @Override
    public String transcribe(byte[] audioBytes, String format, Integer sampleRate) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BusinessException("未检测到可识别的语音数据");
        }
        VoiceProperties.Stt stt = voiceProperties.getStt();
        if (stt.isMockEnabled()) {
            // 便于本地联调：允许将文本字节直接作为“识别结果”回传
            return new String(audioBytes, StandardCharsets.UTF_8).trim();
        }
        if (stt.getEndpoint() == null || stt.getEndpoint().isBlank()) {
            throw new BusinessException("语音识别未配置：请先设置 voice.stt.endpoint");
        }

        String endpoint = stt.getEndpoint().trim();
        if (endpoint.startsWith("wss://") || endpoint.startsWith("ws://")) {
            // 火山实时对话仅支持 PCM 16kHz 单声道 S16LE，不支持 webm/opus
            if ("webm".equalsIgnoreCase(format != null ? format.trim() : "") || "opus".equalsIgnoreCase(format != null ? format.trim() : "")) {
                throw new BusinessException("当前语音识别需要 PCM 16kHz 格式，您正在使用 webm 录音，请改用支持 PCM 的录音方式或使用文本作答");
            }
            return transcribeViaWebSocket(stt, audioBytes);
        }

        RestClient client = RestClient.builder()
                .baseUrl(stt.getEndpoint())
                .defaultHeader("Authorization", "Bearer " + safe(stt.getApiKey()))
                .defaultHeader("Resource-Id", safe(stt.getResourceId()))
                .build();

        Map<String, Object> req = Map.of(
                "appId", safe(stt.getAppId()),
                "audioBase64", Base64.getEncoder().encodeToString(audioBytes),
                "format", (format == null || format.isBlank()) ? stt.getFormat() : format,
                "sampleRate", sampleRate == null ? stt.getSampleRate() : sampleRate
        );

        try {
            Map<?, ?> resp = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(Map.class);
            String text = extractText(resp);
            if (text == null || text.isBlank()) {
                throw new BusinessException("语音识别结果为空，请重试");
            }
            return text.trim();
        } catch (Exception e) {
            log.error("[Voice][STT] 识别失败: {}", e.getMessage());
            throw new BusinessException("语音识别服务暂不可用，请改用文本作答");
        }
    }

    private String transcribeViaWebSocket(VoiceProperties.Stt stt, byte[] audioBytes) {
        try {
            URI uri = URI.create(stt.getEndpoint().trim());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Api-App-ID", safe(stt.getAppId()));
            headers.put("X-Api-Access-Key", safe(stt.getApiKey()));
            headers.put("X-Api-Resource-Id", safe(stt.getResourceId()));
            headers.put("X-Api-App-Key", safe(stt.getAppKey()));
            VolcanoRealtimeSttClient client = new VolcanoRealtimeSttClient(uri, headers);
            String text = client.sendAudioAndGetText(audioBytes);
            if (text == null || text.isBlank()) {
                throw new BusinessException("语音识别结果为空，请重试");
            }
            return text.trim();
        } catch (Exception e) {
            log.error("[Voice][STT] WebSocket 识别失败: {}", e.getMessage());
            throw new BusinessException("语音识别服务暂不可用，请改用文本作答");
        }
    }

    private String extractText(Map<?, ?> resp) {
        if (resp == null) return null;
        Object text = resp.get("text");
        if (text instanceof String s && !s.isBlank()) return s;
        Object data = resp.get("data");
        if (data instanceof Map<?, ?> map) {
            Object result = map.get("text");
            if (result instanceof String s && !s.isBlank()) return s;
            Object alt = map.get("result");
            if (alt instanceof String s && !s.isBlank()) return s;
        }
        Object result = resp.get("result");
        return result instanceof String s ? s : null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
