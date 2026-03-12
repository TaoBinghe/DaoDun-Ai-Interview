package com.daodun.service.impl;

import com.daodun.voice.volcano.Protocol;
import com.daodun.voice.volcano.RealtimeDialogPayloads;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Map;
import java.util.concurrent.*;
import java.util.LinkedHashMap;

/**
 * 火山引擎端到端实时语音 STT 客户端。
 * 与 realtime_dialog 官方 Java 示例一致：二进制协议，event 1 → 100 → 150 → 200(PCM)。
 * 音频格式：PCM 16kHz 单声道 S16LE，块 640 字节约 20ms。
 */
@Slf4j
public class VolcanoRealtimeSttClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int AUDIO_CHUNK_SIZE = 640;
    private static final int SESSION_START_TIMEOUT_SEC = 15;
    private static final int RESULT_TIMEOUT_SEC = 30;
    /** 收到最后一个 ASR 数据后，超过此毫秒数无新数据则结束等待，避免拖慢响应。 */
    private static final long RESULT_IDLE_BREAK_MS = 1200;

    private final URI serverUri;
    private final Map<String, String> headers;
    private final String sessionId;
    private final BlockingQueue<Protocol.Message> incoming = new LinkedBlockingQueue<>();
    private volatile boolean sessionStarted;
    private volatile String lastError;
    private volatile SttClientImpl clientImpl;

    public VolcanoRealtimeSttClient(URI serverUri, Map<String, String> headers) {
        this.serverUri = serverUri;
        this.headers = headers != null ? Map.copyOf(headers) : Map.of();
        this.sessionId = Protocol.generateSessionId();
    }

    private Map<String, String> buildConnectHeaders() {
        Map<String, String> h = new LinkedHashMap<>(headers);
        h.put("X-Api-Connect-Id", sessionId);
        return h;
    }

    /**
     * 发送 PCM 16kHz S16LE 音频，阻塞直到拿到识别文本或超时。
     */
    public String sendAudioAndGetText(byte[] audioBytes) throws Exception {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("audioBytes empty");
        }
        clientImpl = new SttClientImpl(serverUri, buildConnectHeaders(), incoming);
        if (!clientImpl.connectBlocking(SESSION_START_TIMEOUT_SEC + 5, TimeUnit.SECONDS)) {
            throw new IOException("WebSocket 连接失败");
        }
        try {
            clientImpl.send(Protocol.createStartConnectionMessage());
            String startPayload = RealtimeDialogPayloads.buildStartSessionJson();
            clientImpl.send(Protocol.createStartSessionMessage(sessionId, startPayload));

            long deadline = System.currentTimeMillis() + SESSION_START_TIMEOUT_SEC * 1000L;
            while (!sessionStarted && System.currentTimeMillis() < deadline) {
                Protocol.Message msg = incoming.poll(500, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    if (msg.type == Protocol.MsgType.ERROR) {
                        lastError = msg.payload != null ? new String(msg.payload, StandardCharsets.UTF_8) : "unknown";
                        throw new IOException("服务端错误: " + lastError);
                    }
                    if (msg.type == Protocol.MsgType.FULL_SERVER && msg.event == 150) {
                        sessionStarted = true;
                        break;
                    }
                }
            }
            if (!sessionStarted) {
                throw new IOException("会话启动超时，未收到 event 150");
            }

            int offset = 0;
            while (offset < audioBytes.length) {
                int len = Math.min(AUDIO_CHUNK_SIZE, audioBytes.length - offset);
                byte[] chunk = new byte[len];
                System.arraycopy(audioBytes, offset, chunk, 0, len);
                clientImpl.send(Protocol.createAudioMessage(sessionId, chunk));
                offset += len;
            }

            // 官方示例会补一段静音，帮助服务端尽快判定本轮语音结束并输出最终句。
            clientImpl.send(Protocol.createAudioMessage(sessionId, new byte[AUDIO_CHUNK_SIZE]));

            String transcript = "";
            deadline = System.currentTimeMillis() + RESULT_TIMEOUT_SEC * 1000L;
            long lastDataAt = System.currentTimeMillis();
            while (System.currentTimeMillis() < deadline) {
                Protocol.Message msg = incoming.poll(500, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    if (msg.type == Protocol.MsgType.ERROR) {
                        lastError = msg.payload != null ? new String(msg.payload, StandardCharsets.UTF_8) : "unknown";
                        break;
                    }
                    if (msg.type == Protocol.MsgType.FULL_SERVER || msg.type == Protocol.MsgType.FRONT_END_RESULT_SERVER) {
                        if (msg.payload != null && msg.payload.length > 0) {
                            lastDataAt = System.currentTimeMillis();
                            String payloadStr = new String(msg.payload, StandardCharsets.UTF_8);
                            if (log.isInfoEnabled()) {
                                log.info("[VolcanoStt] server msg type={} event={} payload={}", msg.type, msg.event,
                                        payloadStr.length() > 500 ? payloadStr.substring(0, 500) + "..." : payloadStr);
                            }
                            String text = extractTextFromPayload(msg.event, msg.payload);
                            if (text != null && !text.isBlank()) {
                                transcript = mergeTranscriptCandidate(transcript, text);
                            }
                        }
                        if (msg.event == 459) {
                            break;
                        }
                    }
                }
                if (System.currentTimeMillis() - lastDataAt > RESULT_IDLE_BREAK_MS) {
                    break;
                }
            }
            return cleanupTranscript(transcript);
        } finally {
            clientImpl.closeBlocking();
        }
    }

    static String mergeTranscriptCandidate(String current, String candidate) {
        String currentText = cleanupTranscript(current);
        String candidateText = cleanupTranscript(candidate);
        if (candidateText.isBlank()) {
            return currentText;
        }
        if (currentText.isBlank()) {
            return candidateText;
        }

        String currentNormalized = normalizeForCompare(currentText);
        String candidateNormalized = normalizeForCompare(candidateText);
        if (candidateNormalized.isBlank()) {
            return currentText;
        }
        if (candidateNormalized.equals(currentNormalized)) {
            return candidateText.length() >= currentText.length() ? candidateText : currentText;
        }
        if (candidateNormalized.startsWith(currentNormalized) || candidateNormalized.contains(currentNormalized)) {
            return candidateText;
        }
        if (currentNormalized.startsWith(candidateNormalized) || currentNormalized.contains(candidateNormalized)) {
            return currentText;
        }
        // 识别服务通常返回“当前整句”而不是增量 diff，冲突时优先采用最新候选。
        return candidateText;
    }

    static String cleanupTranscript(String transcript) {
        if (transcript == null) {
            return "";
        }
        String cleaned = transcript.replace('\u3000', ' ').trim().replaceAll("\\s+", " ");
        if (cleaned.isBlank()) {
            return "";
        }
        return collapseExactRepeatedPhrase(cleaned);
    }

    private static String normalizeForCompare(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "")
                .toLowerCase();
        return collapseExactRepeatedPhrase(normalized);
    }

    private static String collapseExactRepeatedPhrase(String text) {
        if (text == null || text.length() < 4) {
            return text == null ? "" : text;
        }
        int maxUnitLength = Math.min(12, text.length() / 2);
        for (int unitLength = 2; unitLength <= maxUnitLength; unitLength++) {
            if (text.length() % unitLength != 0) {
                continue;
            }
            String unit = text.substring(0, unitLength);
            int repeat = text.length() / unitLength;
            if (repeat < 2) {
                continue;
            }
            boolean same = true;
            for (int i = 1; i < repeat; i++) {
                if (!unit.equals(text.substring(i * unitLength, (i + 1) * unitLength))) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return unit;
            }
        }
        return text;
    }

    /** 火山 event 451 为 ASR 结果，仅取最终句（避免重复拼接中间结果）。 */
    private static String extractTextFromPayload(int event, byte[] payload) {
        try {
            String json = new String(payload, StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(json);
            if (event == 451) {
                JsonNode extra = root.path("extra");
                if (!extra.isMissingNode()) {
                    JsonNode finish = extra.path("soft_finish_paralinguistic");
                    if (!finish.isMissingNode() && finish.has("asr_text")) {
                        String t = finish.get("asr_text").asText("");
                        if (t != null && !t.isBlank()) return t;
                    }
                    if (extra.path("endpoint").asBoolean(false) && extra.has("origin_text")) {
                        String t = extra.get("origin_text").asText("");
                        if (t != null && !t.isBlank()) return t;
                    }
                }
                JsonNode results = root.path("results");
                if (results.isArray() && results.size() > 0) {
                    JsonNode alts = results.get(0).path("alternatives");
                    if (alts.isArray() && alts.size() > 0 && alts.get(0).has("text")) {
                        String t = alts.get(0).get("text").asText("");
                        if (t != null && !t.isBlank()) return t;
                    }
                }
                return null;
            }
            return extractTextFromPayloadFallback(root);
        } catch (Exception e) {
            log.trace("[VolcanoStt] parse payload: {}", e.getMessage());
            return null;
        }
    }

    private static String extractTextFromPayloadFallback(JsonNode root) {
        try {
            // 火山文档：result 可能为 list，取 result[i].text 拼接
            if (root.has("result") && root.get("result").isArray()) {
                JsonNode arr = root.get("result");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.size(); i++) {
                    JsonNode item = arr.get(i);
                    if (item.has("text")) {
                        String t = item.get("text").asText("");
                        if (t != null && !t.isBlank()) sb.append(t);
                    }
                }
                if (sb.length() > 0) return sb.toString();
            }
            if (root.has("text")) return root.get("text").asText("");
            if (root.has("result")) {
                JsonNode r = root.get("result");
                if (r.isTextual()) return r.asText("");
                if (r.has("text")) return r.get("text").asText("");
            }
            if (root.has("content")) return root.get("content").asText("");
            if (root.has("asr_result")) return root.get("asr_result").asText("");
            if (root.has("message")) return root.get("message").asText("");
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private class SttClientImpl extends WebSocketClient {
        private final BlockingQueue<Protocol.Message> queue;

        SttClientImpl(URI uri, Map<String, String> headers, BlockingQueue<Protocol.Message> queue) {
            super(uri, new Draft_6455(), headers, (int) TimeUnit.SECONDS.toMillis(10));
            this.queue = queue;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            log.debug("[VolcanoStt] WebSocket opened");
        }

        @Override
        public void onMessage(String text) {
            log.debug("[VolcanoStt] text msg: {}", text != null && text.length() > 200 ? text.substring(0, 200) + "..." : text);
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                byte[] data = new byte[bytes.remaining()];
                bytes.get(data);
                Protocol.Message msg = Protocol.unmarshal(data);
                queue.offer(msg);
            } catch (IOException e) {
                log.warn("[VolcanoStt] unmarshal: {}", e.getMessage());
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            log.debug("[VolcanoStt] closed: {} {}", code, reason);
        }

        @Override
        public void onError(Exception ex) {
            log.warn("[VolcanoStt] error: {}", ex.getMessage());
        }
    }
}
