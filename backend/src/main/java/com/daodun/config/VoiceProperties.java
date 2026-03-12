package com.daodun.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "voice")
public class VoiceProperties {

    private boolean enabled = false;
    private String provider = "volcano";

    private final Stt stt = new Stt();
    private final Tts tts = new Tts();

    @Data
    public static class Stt {
        private String endpoint = "";
        private String apiKey = "";
        private String appId = "";
        private String appKey = "";
        private String resourceId = "";
        private String format = "webm";
        private Integer sampleRate = 16000;
        private boolean mockEnabled = false;
    }

    @Data
    public static class Tts {
        private String endpoint = "https://sami.bytedance.com/api/v1/invoke";
        private String apiKey = "";
        private String appKey = "";
        private String resourceId = "volc.service_type.10029";
        private String version = "v4";
        private String namespace = "TTS";
        private String speaker = "zh_female_vv_jupiter_bigtts";
        private String format = "mp3";
        private Integer sampleRate = 24000;
        private boolean enableTimestamp = true;
        private boolean mockEnabled = false;
    }
}
