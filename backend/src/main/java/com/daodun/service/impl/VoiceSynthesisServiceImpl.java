package com.daodun.service.impl;

import com.daodun.config.VoiceProperties;
import com.daodun.dto.voice.TtsResult;
import com.daodun.service.VoiceSynthesisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceSynthesisServiceImpl implements VoiceSynthesisService {

    private final VoiceProperties voiceProperties;

    @Override
    public TtsResult synthesize(String text) {
        if (text == null || text.isBlank()) {
            return TtsResult.builder().subtitle("").mimeType("audio/wav").build();
        }

        VoiceProperties.Tts tts = voiceProperties.getTts();
        VoiceProperties.Stt stt = voiceProperties.getStt();
        if (tts.isMockEnabled()) {
            return TtsResult.builder()
                    .subtitle(text)
                    .mimeType("audio/wav")
                    .audioBase64(null)
                    .build();
        }
        if (stt.getEndpoint() == null || stt.getEndpoint().isBlank()) {
            log.warn("[Voice][RealtimeTTS] STT endpoint 未配置，无法通过端到端语音输出");
            return TtsResult.builder().subtitle(text).mimeType("audio/wav").build();
        }

        try {
            URI uri = URI.create(stt.getEndpoint().trim());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Api-App-ID", safe(stt.getAppId()));
            headers.put("X-Api-Access-Key", safe(stt.getApiKey()));
            headers.put("X-Api-Resource-Id", safe(stt.getResourceId()));
            headers.put("X-Api-App-Key", safe(stt.getAppKey()));

            VolcanoRealtimeVoiceOutputClient client = new VolcanoRealtimeVoiceOutputClient(uri, headers);
            byte[] wavBytes = client.synthesize(text, safe(tts.getSpeaker()));
            String audioBase64 = Base64.getEncoder().encodeToString(wavBytes);
            return TtsResult.builder()
                    .subtitle(text)
                    .audioBase64(audioBase64)
                    .mimeType("audio/wav")
                    .build();
        } catch (Exception e) {
            log.warn("[Voice][RealtimeTTS] 合成失败，降级字幕: {}", e.getMessage());
            return TtsResult.builder()
                    .subtitle(text)
                    .mimeType("audio/wav")
                    .audioBase64(null)
                    .build();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
