package com.daodun.service.impl;

import com.daodun.common.BusinessException;
import com.daodun.config.EmotionProperties;
import com.daodun.service.EmotionAnalysisService;
import com.daodun.service.model.EmotionAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionAnalysisServiceImpl implements EmotionAnalysisService {

    private final EmotionProperties emotionProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public EmotionAnalysisResult analyzeFrame(Long sessionId, String imageBase64, Long capturedAt) {
        if (!emotionProperties.isEnabled()) {
            return EmotionAnalysisResult.builder()
                    .status("disabled")
                    .hasFace(false)
                    .capturedAt(capturedAt)
                    .build();
        }
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new BusinessException("图像帧为空");
        }
        String endpoint = emotionProperties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException("情绪识别服务未配置 endpoint");
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("sessionId", sessionId);
        req.put("imageBase64", imageBase64);
        req.put("source", "interview");
        req.put("capturedAt", capturedAt);
        try {
            String requestJson = objectMapper.writeValueAsString(req);
            byte[] requestBytes = requestJson.getBytes(StandardCharsets.UTF_8);
            log.debug("[Emotion] request endpoint={} jsonBytes={}", endpoint.trim(), requestBytes.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.trim()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[Emotion] 调用识别服务失败 status={} body={}", response.statusCode(), response.body());
                return EmotionAnalysisResult.builder()
                        .status("error")
                        .hasFace(false)
                        .capturedAt(capturedAt)
                        .build();
            }

            Map<?, ?> resp = objectMapper.readValue(response.body(), Map.class);
            String emotion = stringValue(resp != null ? resp.get("dominantEmotion") : null);
            Double confidence = doubleValue(resp != null ? resp.get("confidence") : null);
            Boolean hasFace = boolValue(resp != null ? resp.get("hasFace") : null);
            Long responseCapturedAt = longValue(resp != null ? resp.get("capturedAt") : null);
            return EmotionAnalysisResult.builder()
                    .emotion(emotion)
                    .confidence(confidence)
                    .hasFace(hasFace != null ? hasFace : false)
                    .capturedAt(responseCapturedAt != null ? responseCapturedAt : capturedAt)
                    .status("ok")
                    .build();
        } catch (Exception e) {
            log.warn("[Emotion] 调用识别服务失败: {}", e.getMessage());
            return EmotionAnalysisResult.builder()
                    .status("error")
                    .hasFace(false)
                    .capturedAt(capturedAt)
                    .build();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean boolValue(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s && !s.isBlank()) return Boolean.parseBoolean(s);
        return null;
    }

    private Long longValue(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
