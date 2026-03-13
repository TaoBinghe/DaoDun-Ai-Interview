package com.daodun.voice.volcano;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 豆包语音 - 精品长文本语音合成（异步）：submit → 轮询 query → 下载 audio_url。
 * 鉴权：Resource-Id + Authorization: Bearer; {access_token}（见 https://www.volcengine.com/docs/6561/1105162 ）
 * 接口：https://www.volcengine.com/docs/6561/1096680
 */
@Slf4j
public class OpenspeechLongTextTtsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SUBMIT_URL = "https://openspeech.bytedance.com/api/v1/tts_async/submit";
    private static final String QUERY_URL = "https://openspeech.bytedance.com/api/v1/tts_async/query";
    private static final String RESOURCE_ID = "volc.tts_async.default";
    private static final int POLL_INTERVAL_MS = 2_000;
    private static final int POLL_TIMEOUT_MS = 90_000;

    private final String accessToken;
    private final String appId;
    private final String voiceType;
    private final String format;
    private final int sampleRate;

    public OpenspeechLongTextTtsClient(String accessToken, String appId, String voiceType, String format, int sampleRate) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.appId = appId == null ? "" : appId.trim();
        this.voiceType = (voiceType == null || voiceType.isBlank()) ? "BV701_streaming" : voiceType.trim();
        this.format = (format == null || format.isBlank()) ? "mp3" : format.trim();
        this.sampleRate = sampleRate > 0 ? sampleRate : 24000;
    }

    /**
     * 同步合成：提交任务后轮询直到成功或超时，返回音频 base64。
     */
    public byte[] synthesize(String text) throws Exception {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        String reqid = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().substring(0, 12);

        String submitBody = MAPPER.writeValueAsString(Map.of(
                "appid", appId,
                "reqid", reqid,
                "text", text,
                "format", format,
                "voice_type", voiceType,
                "sample_rate", sampleRate
        ));

        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(SUBMIT_URL))
                .header("Content-Type", "application/json")
                .header("Resource-Id", RESOURCE_ID)
                .header("Authorization", "Bearer; " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(submitBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> submitResp = HttpClient.newHttpClient().send(submitReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String submitRespBody = submitResp.body();

        if (submitResp.statusCode() >= 400) {
            log.warn("[OpenspeechLongTTS] submit 失败 status={} body={}", submitResp.statusCode(), truncate(submitRespBody, 400));
            throw new IllegalStateException("submit 失败: HTTP " + submitResp.statusCode());
        }

        JsonNode submitRoot = MAPPER.readTree(submitRespBody);
        if (submitRoot.has("code") && submitRoot.get("code").asInt(0) != 0) {
            int code = submitRoot.path("code").asInt(0);
            String message = submitRoot.path("message").asText("");
            log.warn("[OpenspeechLongTTS] submit 业务失败 code={} message={}", code, message);
            throw new IllegalStateException("submit 失败: " + code + " " + message);
        }

        String taskId = submitRoot.path("task_id").asText(null);
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalStateException("submit 返回无 task_id");
        }

        log.info("[OpenspeechLongTTS] submit 成功 taskId={} 开始轮询", taskId);

        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS);

            String queryUrl = QUERY_URL + "?appid=" + appId + "&task_id=" + taskId;
            HttpRequest queryReq = HttpRequest.newBuilder()
                    .uri(URI.create(queryUrl))
                    .header("Resource-Id", RESOURCE_ID)
                    .header("Authorization", "Bearer; " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> queryResp = HttpClient.newHttpClient().send(queryReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String queryBody = queryResp.body();

            if (queryResp.statusCode() >= 400) {
                log.warn("[OpenspeechLongTTS] query 失败 status={} body={}", queryResp.statusCode(), truncate(queryBody, 300));
                continue;
            }

            JsonNode queryRoot = MAPPER.readTree(queryBody);
            int taskStatus = queryRoot.path("task_status").asInt(-1);
            if (queryRoot.has("code") && queryRoot.get("code").asInt(0) != 0) {
                log.warn("[OpenspeechLongTTS] query 业务失败 code={} message={}", queryRoot.path("code").asInt(), queryRoot.path("message").asText(""));
                continue;
            }

            if (taskStatus == 2) {
                String msg = queryRoot.path("message").asText("合成失败");
                throw new IllegalStateException("合成失败: " + msg);
            }
            if (taskStatus == 1) {
                String audioUrl = queryRoot.path("audio_url").asText(null);
                if (audioUrl == null || audioUrl.isBlank()) {
                    throw new IllegalStateException("query 返回无 audio_url");
                }
                return downloadAudio(audioUrl);
            }
        }

        throw new IllegalStateException("轮询超时，未在 " + (POLL_TIMEOUT_MS / 1000) + "s 内收到合成结果");
    }

    private byte[] downloadAudio(String audioUrl) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(audioUrl))
                .GET()
                .build();
        HttpResponse<byte[]> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("下载音频失败: HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
