package com.daodun.voice.volcano;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.zip.GZIPInputStream;

/**
 * 豆包语音普通版 TTS（WebSocket 二进制协议）：
 * wss://openspeech.bytedance.com/api/v1/tts/ws_binary
 */
@Slf4j
public class OpenspeechWebsocketTtsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int RESULT_TIMEOUT_SEC = 25;

    private static final int MSG_TYPE_FULL_CLIENT = 0x1;
    private static final int MSG_TYPE_AUDIO_ONLY_SERVER = 0xB;
    private static final int MSG_TYPE_ERROR = 0xF;
    private static final int SERIALIZATION_JSON = 0x1;
    private static final int COMPRESSION_NONE = 0x0;
    private static final int COMPRESSION_GZIP = 0x1;

    private final URI endpoint;
    private final String accessToken;
    private final String appId;
    private final String cluster;
    private final String voiceType;
    private final String format;
    private final int sampleRate;

    public OpenspeechWebsocketTtsClient(
            String endpoint,
            String accessToken,
            String appId,
            String cluster,
            String voiceType,
            String format,
            int sampleRate
    ) {
        this.endpoint = URI.create(endpoint);
        this.accessToken = trim(accessToken);
        this.appId = trim(appId);
        this.cluster = trim(cluster).isBlank() ? "volcano_tts" : trim(cluster);
        this.voiceType = trim(voiceType).isBlank() ? "BV701_streaming" : trim(voiceType);
        this.format = trim(format).isBlank() ? "mp3" : trim(format);
        this.sampleRate = sampleRate > 0 ? sampleRate : 24000;
    }

    public byte[] synthesize(String text) throws Exception {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }

        BlockingQueue<WsEvent> queue = new LinkedBlockingQueue<>();
        ClientImpl client = new ClientImpl(endpoint, buildHeaders(), queue);
        if (!client.connectBlocking(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            throw new IllegalStateException("普通版 TTS WebSocket 连接失败");
        }

        try {
            client.send(buildSubmitFrame(text));
            return collectAudio(queue);
        } finally {
            client.closeBlocking();
        }
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        // 官方文档要求 Bearer 与 token 使用分号分隔。
        headers.put("Authorization", "Bearer; " + accessToken);
        return headers;
    }

    private byte[] buildSubmitFrame(String text) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("app", Map.of(
                "appid", appId,
                "token", accessToken,
                "cluster", cluster
        ));
        root.put("user", Map.of("uid", "ai-interview"));
        root.put("audio", Map.of(
                "voice_type", voiceType,
                "encoding", format,
                "sample_rate", sampleRate
        ));
        root.put("request", Map.of(
                "reqid", UUID.randomUUID().toString(),
                "text", text,
                "text_type", "plain",
                "operation", "submit"
        ));

        byte[] payload = MAPPER.writeValueAsBytes(root);
        ByteBuffer buf = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) 0x11); // version=1, header size=1(4 bytes)
        buf.put((byte) ((MSG_TYPE_FULL_CLIENT << 4) | 0x0)); // full client request
        buf.put((byte) ((SERIALIZATION_JSON << 4) | COMPRESSION_NONE));
        buf.put((byte) 0x00);
        buf.putInt(payload.length);
        buf.put(payload);
        return buf.array();
    }

    private byte[] collectAudio(BlockingQueue<WsEvent> queue) throws Exception {
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + RESULT_TIMEOUT_SEC * 1000L;
        boolean gotAnyAudio = false;

        while (System.currentTimeMillis() < deadline) {
            WsEvent evt = queue.poll(500, TimeUnit.MILLISECONDS);
            if (evt == null) {
                continue;
            }
            if (evt.errorMessage != null && !evt.errorMessage.isBlank()) {
                throw new IllegalStateException(evt.errorMessage);
            }
            if (evt.audioChunk != null && evt.audioChunk.length > 0) {
                audio.write(evt.audioChunk);
                gotAnyAudio = true;
            }
            if (evt.isLastAudioPacket) {
                break;
            }
        }

        if (!gotAnyAudio) {
            throw new IllegalStateException("普通版 TTS 未收到音频数据");
        }
        return audio.toByteArray();
    }

    private static WsEvent parseFrame(byte[] frame) {
        try {
            if (frame == null || frame.length < 4) {
                return WsEvent.error("普通版 TTS 返回了无效帧");
            }
            ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN);
            buf.get(); // version + header size
            int typeAndFlags = buf.get() & 0xFF;
            int serializationCompression = buf.get() & 0xFF;
            buf.get(); // reserved

            int msgType = (typeAndFlags >> 4) & 0x0F;
            int msgFlags = typeAndFlags & 0x0F;
            int compression = serializationCompression & 0x0F;

            if (msgType == MSG_TYPE_AUDIO_ONLY_SERVER) {
                int seq = 0;
                if (msgFlags != 0) {
                    seq = buf.getInt();
                }
                int payloadSize = buf.getInt();
                if (payloadSize < 0 || payloadSize > buf.remaining()) {
                    return WsEvent.error("普通版 TTS 返回 payload 长度异常");
                }
                byte[] payload = new byte[payloadSize];
                buf.get(payload);
                byte[] audio = decodePayload(payload, compression);
                boolean isLast = msgFlags == 0x2 || msgFlags == 0x3 || seq < 0;
                return WsEvent.audio(audio, isLast);
            }

            if (msgType == MSG_TYPE_ERROR) {
                long code = buf.getInt() & 0xFFFFFFFFL;
                int msgSize = buf.getInt();
                if (msgSize < 0 || msgSize > buf.remaining()) {
                    return WsEvent.error("普通版 TTS 错误帧长度异常 code=" + code);
                }
                byte[] errPayload = new byte[msgSize];
                buf.get(errPayload);
                byte[] decoded = decodePayload(errPayload, compression);
                String err = new String(decoded, StandardCharsets.UTF_8);
                return WsEvent.error("普通版 TTS 服务端错误 code=" + code + " msg=" + err);
            }

            return WsEvent.empty();
        } catch (Exception e) {
            return WsEvent.error("普通版 TTS 解析帧失败: " + e.getMessage());
        }
    }

    private static byte[] decodePayload(byte[] payload, int compression) throws Exception {
        if (compression != COMPRESSION_GZIP) {
            return payload;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(payload);
             GZIPInputStream gzip = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = gzip.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return out.toByteArray();
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static final class WsEvent {
        private final byte[] audioChunk;
        private final boolean isLastAudioPacket;
        private final String errorMessage;

        private WsEvent(byte[] audioChunk, boolean isLastAudioPacket, String errorMessage) {
            this.audioChunk = audioChunk;
            this.isLastAudioPacket = isLastAudioPacket;
            this.errorMessage = errorMessage;
        }

        private static WsEvent audio(byte[] audioChunk, boolean isLast) {
            return new WsEvent(audioChunk, isLast, null);
        }

        private static WsEvent error(String message) {
            return new WsEvent(null, false, message);
        }

        private static WsEvent empty() {
            return new WsEvent(null, false, null);
        }
    }

    private static final class ClientImpl extends WebSocketClient {
        private final BlockingQueue<WsEvent> queue;

        private ClientImpl(URI serverUri, Map<String, String> headers, BlockingQueue<WsEvent> queue) {
            super(serverUri, new Draft_6455(), headers, (int) TimeUnit.SECONDS.toMillis(10));
            this.queue = queue;
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            log.debug("[OpenspeechWsTTS] connected");
        }

        @Override
        public void onMessage(String message) {
            log.debug("[OpenspeechWsTTS] text msg={}", message);
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            byte[] data = new byte[bytes.remaining()];
            bytes.get(data);
            queue.offer(parseFrame(data));
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            log.debug("[OpenspeechWsTTS] closed code={} reason={}", code, reason);
        }

        @Override
        public void onError(Exception ex) {
            queue.offer(WsEvent.error("普通版 TTS WebSocket 异常: " + ex.getMessage()));
        }
    }
}
