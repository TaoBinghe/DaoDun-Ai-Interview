package com.daodun.voice.volcano;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 火山引擎流式语音识别（api/v2/asr）：
 * wss://openspeech.bytedance.com/api/v2/asr
 * 协议：先发 full client request (JSON)，再多次发送 audio only request (raw PCM)，最后一包带 last 标志。
 * 鉴权：Authorization: Bearer; {token}，Resource-Id 见控制台/文档。
 */
@Slf4j
public class OpenspeechAsrV2StreamingClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int RESULT_TIMEOUT_SEC = 30;

    private static final byte FULL_CLIENT_HEADER_0 = 0x11;
    private static final byte FULL_CLIENT_HEADER_1 = 0x10;
    private static final byte FULL_CLIENT_HEADER_2 = 0x10;
    private static final byte FULL_CLIENT_HEADER_3 = 0x00;

    private static final byte AUDIO_ONLY_HEADER_0 = 0x11;
    private static final byte AUDIO_ONLY_HEADER_1 = 0x20;
    private static final byte AUDIO_ONLY_LAST_HEADER_1 = 0x22;
    private static final byte AUDIO_ONLY_HEADER_2 = 0x00;
    private static final byte AUDIO_ONLY_HEADER_3 = 0x00;

    private final URI endpoint;
    private final String token;
    private final String appId;
    private final String cluster;
    private final String resourceId;
    private final String reqid;
    private static final String EVENT_MARKER = "__ASR_EVT__";
    private static final String ERROR_PREFIX = "__ASR_ERR__:";
    private final BlockingQueue<String> resultQueue = new LinkedBlockingQueue<>();
    private volatile WsImpl clientImpl;

    public OpenspeechAsrV2StreamingClient(String endpoint, String token, String appId, String cluster, String resourceId) {
        this.endpoint = URI.create(endpoint);
        this.token = token == null ? "" : token.trim();
        this.appId = appId == null ? "" : appId.trim();
        this.cluster = (cluster == null || cluster.isBlank()) ? "volcano_asr" : cluster.trim();
        this.resourceId = (resourceId == null || resourceId.isBlank()) ? "volc.asr.default" : resourceId.trim();
        this.reqid = UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 建立连接并发送 full client request，之后可多次调用 sendAudioChunk / sendAudioLast。
     */
    public void connectAndSendConfig() throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer; " + token);
        headers.put("Resource-Id", resourceId);
        clientImpl = new WsImpl(endpoint, headers, resultQueue);
        if (!clientImpl.connectBlocking(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            throw new IllegalStateException("ASR WebSocket 连接失败");
        }
        byte[] fullClientPayload = buildFullClientRequestJson().getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(8 + fullClientPayload.length).order(ByteOrder.BIG_ENDIAN);
        frame.put(FULL_CLIENT_HEADER_0);
        frame.put(FULL_CLIENT_HEADER_1);
        frame.put(FULL_CLIENT_HEADER_2);
        frame.put(FULL_CLIENT_HEADER_3);
        frame.putInt(fullClientPayload.length);
        frame.put(fullClientPayload);
        clientImpl.send(frame.array());
        log.debug("[ASR-V2] full client request sent reqid={}", reqid);
    }

    /**
     * 发送一包 PCM 音频（非最后一包）。
     */
    public void sendAudioChunk(byte[] pcm) throws Exception {
        if (pcm == null || pcm.length == 0) return;
        sendAudioFrame(pcm, false);
    }

    /**
     * 发送最后一包 PCM（或空包）并标记结束，然后阻塞直到拿到最终识别文本（取最后一次非空结果）。
     */
    public String sendAudioLastAndGetResult(byte[] lastPcm) throws Exception {
        sendAudioFrame(lastPcm != null ? lastPcm : new byte[0], true);
        long deadline = System.currentTimeMillis() + RESULT_TIMEOUT_SEC * 1000L;
        long lastDataAt = System.currentTimeMillis();
        String lastText = null;
        String lastError = null;
        while (System.currentTimeMillis() < deadline) {
            String text = resultQueue.poll(500, TimeUnit.MILLISECONDS);
            if (text != null) {
                lastDataAt = System.currentTimeMillis();
                if (text.startsWith(ERROR_PREFIX)) {
                    lastError = text.substring(ERROR_PREFIX.length());
                    log.warn("[ASR-V2] 服务端返回错误: {}", lastError);
                    continue;
                }
                if (!EVENT_MARKER.equals(text) && !text.isBlank()) {
                    lastText = text;
                }
            }
            if (System.currentTimeMillis() - lastDataAt > 1500) break;
        }
        if ((lastText == null || lastText.isBlank()) && lastError != null && !lastError.isBlank()) {
            throw new IllegalStateException("ASR 服务端错误: " + lastError);
        }
        return lastText;
    }

    public void close() {
        if (clientImpl != null) {
            try {
                clientImpl.closeBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            clientImpl = null;
        }
    }

    private void sendAudioFrame(byte[] pcm, boolean last) {
        byte typeFlag = last ? AUDIO_ONLY_LAST_HEADER_1 : AUDIO_ONLY_HEADER_1;
        ByteBuffer frame = ByteBuffer.allocate(8 + pcm.length).order(ByteOrder.BIG_ENDIAN);
        frame.put(AUDIO_ONLY_HEADER_0);
        frame.put(typeFlag);
        frame.put(AUDIO_ONLY_HEADER_2);
        frame.put(AUDIO_ONLY_HEADER_3);
        frame.putInt(pcm.length);
        frame.put(pcm);
        clientImpl.send(frame.array());
    }

    private String buildFullClientRequestJson() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("app", Map.of("appid", appId, "token", token, "cluster", cluster));
        root.put("user", Map.of("uid", "ai-interview-" + reqid));
        root.put("audio", Map.of(
                "format", "raw",
                "rate", 16000,
                "bits", 16,
                "channel", 1
        ));
        root.put("request", Map.of(
                "reqid", reqid,
                "sequence", 1,
                "nbest", 1,
                "vad_signal", true,
                "start_silence_time", "5000",
                "vad_silence_time", "1000"
        ));
        return MAPPER.writeValueAsString(root);
    }

    /**
     * 解析服务端 full server response (JSON)，提取 result[].text。
     */
    static String parseFullServerResponse(byte[] payload) {
        if (payload == null || payload.length == 0) return null;
        try {
            String json = new String(payload, StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(json);
            int code = root.path("code").asInt(-1);
            if (code != 1000) {
                log.warn("[ASR-V2] server code={} message={}", code, root.path("message").asText(""));
                return null;
            }
            JsonNode result = root.path("result");
            if (!result.isArray()) return null;
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : result) {
                String text = item.path("text").asText(null);
                if (text != null && !text.isBlank()) sb.append(text);
            }
            return sb.length() > 0 ? sb.toString().trim() : null;
        } catch (Exception e) {
            log.trace("[ASR-V2] parse response: {}", e.getMessage());
            return null;
        }
    }

    private static final class WsImpl extends WebSocketClient {
        private final BlockingQueue<String> resultQueue;

        WsImpl(URI uri, Map<String, String> headers, BlockingQueue<String> resultQueue) {
            super(uri, new Draft_6455(), headers, (int) TimeUnit.SECONDS.toMillis(10));
            this.resultQueue = resultQueue;
        }

        @Override
        public void onOpen(ServerHandshake h) {
            log.debug("[ASR-V2] connected");
        }

        @Override
        public void onMessage(String text) {
            log.debug("[ASR-V2] text msg: {}", text);
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            byte[] data = new byte[bytes.remaining()];
            bytes.get(data);
            if (data.length < 8) return;
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            buf.get();
            int typeAndFlags = buf.get() & 0xFF;
            int msgType = (typeAndFlags >> 4) & 0x0F;
            buf.get();
            buf.get();
            if (msgType == 0xF) {
                long errCode = buf.getInt() & 0xFFFFFFFFL;
                int errSize = buf.getInt();
                if (errSize >= 0 && errSize <= buf.remaining()) {
                    byte[] err = new byte[errSize];
                    buf.get(err);
                    String errMsg = new String(err, StandardCharsets.UTF_8);
                    resultQueue.offer(ERROR_PREFIX + "code=" + errCode + ", msg=" + errMsg);
                } else {
                    resultQueue.offer(ERROR_PREFIX + "code=" + errCode + ", invalid error size");
                }
                return;
            }
            int payloadSize = buf.getInt();
            if (payloadSize < 0 || payloadSize > buf.remaining()) return;
            byte[] payload = new byte[payloadSize];
            buf.get(payload);
            if (msgType == 0x9) {
                resultQueue.offer(EVENT_MARKER);
                String text = parseFullServerResponse(payload);
                if (text != null && !text.isBlank()) {
                    resultQueue.offer(text);
                }
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            log.debug("[ASR-V2] closed {} {}", code, reason);
        }

        @Override
        public void onError(Exception ex) {
            log.warn("[ASR-V2] error: {}", ex.getMessage());
        }
    }
}
