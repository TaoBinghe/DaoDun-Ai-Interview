package com.daodun.tools;

import com.daodun.service.impl.VolcanoRealtimeVoiceOutputClient;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 端到端语音对话链路诊断工具（独立 main，可直接运行）。
 *
 * 用法（PowerShell）：
 * 1) 设置环境变量：
 *    $env:VOLCANO_STT_ENDPOINT="wss://openspeech.bytedance.com/api/v3/realtime/dialogue"
 *    $env:VOLCANO_STT_APP_ID="..."
 *    $env:VOLCANO_STT_API_KEY="..."
 *    $env:VOLCANO_STT_APP_KEY="PlgvMymc7f3tQnJ6"
 *    $env:VOLCANO_STT_RESOURCE_ID="volc.speech.dialog"
 *    $env:VOLCANO_TTS_SPEAKER="zh_female_vv_jupiter_bigtts"
 * 2) 运行：
 *    mvnw -q -DskipTests exec:java "-Dexec.mainClass=com.daodun.tools.RealtimeVoiceDebugMain" "-Dexec.args=你好，今天面试Java后端开发岗位，请先做个自我介绍。"
 */
public class RealtimeVoiceDebugMain {

    public static void main(String[] args) throws Exception {
        String endpoint = env("VOLCANO_STT_ENDPOINT", "wss://openspeech.bytedance.com/api/v3/realtime/dialogue");
        String appId = env("VOLCANO_STT_APP_ID", "");
        String apiKey = env("VOLCANO_STT_API_KEY", "");
        String appKey = env("VOLCANO_STT_APP_KEY", "PlgvMymc7f3tQnJ6");
        String resourceId = env("VOLCANO_STT_RESOURCE_ID", "volc.speech.dialog");
        String speaker = env("VOLCANO_TTS_SPEAKER", "zh_female_vv_jupiter_bigtts");
        String text = (args != null && args.length > 0 && args[0] != null && !args[0].isBlank())
                ? args[0]
                : "你好，今天面试Java后端开发岗位，请先做个自我介绍。";

        System.out.println("[RealtimeVoiceDebug] endpoint=" + endpoint);
        System.out.println("[RealtimeVoiceDebug] appIdPresent=" + !appId.isBlank());
        System.out.println("[RealtimeVoiceDebug] apiKeyPresent=" + !apiKey.isBlank());
        System.out.println("[RealtimeVoiceDebug] resourceId=" + resourceId);
        System.out.println("[RealtimeVoiceDebug] speaker=" + speaker);
        System.out.println("[RealtimeVoiceDebug] text=" + text);

        if (appId.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("缺少 VOLCANO_STT_APP_ID 或 VOLCANO_STT_API_KEY");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Api-App-ID", appId);
        headers.put("X-Api-Access-Key", apiKey);
        headers.put("X-Api-Resource-Id", resourceId);
        headers.put("X-Api-App-Key", appKey);

        VolcanoRealtimeVoiceOutputClient client = new VolcanoRealtimeVoiceOutputClient(URI.create(endpoint), headers);
        long start = System.currentTimeMillis();
        byte[] wavBytes = client.synthesize(text, speaker);
        long elapsed = System.currentTimeMillis() - start;

        Path out = Path.of("realtime-voice-debug.wav");
        Files.write(out, wavBytes);
        System.out.println("[RealtimeVoiceDebug] success, bytes=" + wavBytes.length + ", elapsedMs=" + elapsed);
        System.out.println("[RealtimeVoiceDebug] wavSaved=" + out.toAbsolutePath());
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v.trim();
    }
}
