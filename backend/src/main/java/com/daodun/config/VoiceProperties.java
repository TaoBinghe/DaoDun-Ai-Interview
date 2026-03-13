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
        /** 流式 ASR (api/v2/asr) 必填：控制台 Cluster ID */
        private String cluster = "volcano_asr";
        private String resourceId = "volc.asr.default";
        private String format = "webm";
        private Integer sampleRate = 16000;
        private boolean mockEnabled = false;
    }

    @Data
    public static class Tts {
        private String endpoint = "https://sami.bytedance.com/api/v1/invoke";
        /** 用于 GetToken 的火山引擎 access_key（与 secretKey 同时配置时优先用临时 token 鉴权） */
        private String accessKey = "";
        /** 用于 GetToken 的火山引擎 secret_key */
        private String secretKey = "";
        /** 未使用 GetToken 时作为 body.token 传入（易导致 401 IllegalToken，建议配置 accessKey/secretKey） */
        private String apiKey = "";
        private String appKey = "";
        /** 豆包精品长文本用：控制台 APP ID，与 appKey 二选一或同值 */
        private String appId = "";
        /** 豆包普通版 WebSocket TTS 用：cluster（示例 volcano_tts） */
        private String cluster = "volcano_tts";
        private String resourceId = "volc.service_type.10029";
        private String version = "v4";
        private String namespace = "TTS";
        private String speaker = "zh_female_vv_jupiter_bigtts";
        /** 豆包精品长文本用：voice_type，见文档音色列表，默认 BV701_streaming */
        private String voiceType = "BV701_streaming";
        private String format = "mp3";
        private Integer sampleRate = 24000;
        private boolean enableTimestamp = true;
        private boolean mockEnabled = false;
    }
}
