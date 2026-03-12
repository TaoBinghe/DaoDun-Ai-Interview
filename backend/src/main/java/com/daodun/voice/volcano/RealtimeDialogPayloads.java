package com.daodun.voice.volcano;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 与 realtime_dialog 官方示例一致的请求载荷（仅 STT 会话所需部分）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RealtimeDialogPayloads {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 16kHz 单声道 S16LE，与官方 Config 一致 */
    public static final String PCM_FORMAT = "pcm_s16le";
    public static final int OUTPUT_SAMPLE_RATE = 24000;
    public static final String DEFAULT_SPEAKER = "zh_female_vv_jupiter_bigtts";

    public static class StartSessionPayload {
        public ASRPayload asr = new ASRPayload();
        public TTSPayload tts = new TTSPayload();
        public DialogPayload dialog = new DialogPayload();
    }

    public static class ASRPayload {
        public Map<String, Object> extra = new HashMap<>();
    }

    public static class TTSPayload {
        public String speaker = DEFAULT_SPEAKER;
        public AudioConfig audio_config = new AudioConfig();
    }

    public static class AudioConfig {
        public int channel = 1;
        public String format = PCM_FORMAT;
        public int sample_rate = OUTPUT_SAMPLE_RATE;
    }

    public static class DialogPayload {
        public String dialog_id = "";
        public String bot_name = "豆包";
        public String system_role = "你使用活泼灵动的女声，性格开朗，热爱生活。";
        public String speaking_style = "你的说话风格简洁明了，语速适中，语调自然。";
        public LocationInfo location = new LocationInfo();
        public Map<String, Object> extra = new HashMap<>();
    }

    public static class LocationInfo {
        public double longitude = 0.0;
        public double latitude = 0.0;
        public String city = "北京";
        public String country = "中国";
        public String province = "北京";
        public String district = "";
        public String town = "";
        public String country_code = "CN";
        public String address = "";
    }

    /**
     * 构建 event 100 的 JSON 载荷，input_mod=audio。
     */
    public static String buildStartSessionJson() throws Exception {
        return buildStartSessionJson("audio", DEFAULT_SPEAKER);
    }

    /**
     * 构建 event 100 的 JSON 载荷。
     *
     * @param inputMod audio/text/audio_file
     * @param speaker  语音说话人，不传使用默认值
     */
    public static String buildStartSessionJson(String inputMod, String speaker) throws Exception {
        StartSessionPayload payload = new StartSessionPayload();
        if (speaker != null && !speaker.isBlank()) {
            payload.tts.speaker = speaker.trim();
        }
        Map<String, Object> extra = new HashMap<>();
        extra.put("strict_audit", false);
        extra.put("audit_response", "抱歉这个问题我无法回答，你可以换个其他话题，我会尽力为你提供帮助。");
        extra.put("input_mod", (inputMod == null || inputMod.isBlank()) ? "audio" : inputMod.trim());
        extra.put("model", "O");
        payload.dialog.extra = extra;
        return MAPPER.writeValueAsString(payload);
    }

    private RealtimeDialogPayloads() {}
}
