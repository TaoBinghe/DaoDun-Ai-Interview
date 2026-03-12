package com.daodun.service.impl;

import com.daodun.voice.volcano.Protocol;
import com.daodun.voice.volcano.RealtimeDialogPayloads;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 基于火山端到端实时语音对话，直接将文本转为音频输出。
 * 流程：event 1 -> 100(input_mod=text) -> 501(content) -> 收集 AUDIO_ONLY_SERVER 音频。
 */
@Slf4j
public class VolcanoRealtimeVoiceOutputClient {

    private static final int SESSION_START_TIMEOUT_SEC = 15;
    private static final int RESULT_TIMEOUT_SEC = 30;
    private static final long AUDIO_IDLE_BREAK_MS = 1200;

    private final URI serverUri;
    private final Map<String, String> headers;
    private final String sessionId;
    private final BlockingQueue<Protocol.Message> incoming = new LinkedBlockingQueue<>();
    private volatile boolean sessionStarted;
    private volatile ClientImpl clientImpl;

    public VolcanoRealtimeVoiceOutputClient(URI serverUri, Map<String, String> headers) {
        this.serverUri = serverUri;
        this.headers = headers != null ? Map.copyOf(headers) : Map.of();
        this.sessionId = Protocol.generateSessionId();
    }

    public byte[] synthesize(String text, String speaker) throws Exception {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        clientImpl = new ClientImpl(serverUri, buildConnectHeaders(), incoming);
        if (!clientImpl.connectBlocking(SESSION_START_TIMEOUT_SEC + 5, TimeUnit.SECONDS)) {
            throw new IOException("WebSocket 连接失败");
        }
        try {
            clientImpl.send(Protocol.createStartConnectionMessage());
            clientImpl.send(Protocol.createStartSessionMessage(sessionId, RealtimeDialogPayloads.buildStartSessionJson("text", speaker)));
            waitSessionStarted();

            clientImpl.send(Protocol.createChatTextQueryMessage(sessionId, text));
            return waitAudioResult();
        } finally {
            clientImpl.closeBlocking();
        }
    }

    private Map<String, String> buildConnectHeaders() {
        Map<String, String> h = new LinkedHashMap<>(headers);
        h.put("X-Api-Connect-Id", sessionId);
        return h;
    }

    private void waitSessionStarted() throws Exception {
        long deadline = System.currentTimeMillis() + SESSION_START_TIMEOUT_SEC * 1000L;
        while (!sessionStarted && System.currentTimeMillis() < deadline) {
            Protocol.Message msg = incoming.poll(500, TimeUnit.MILLISECONDS);
            if (msg == null) {
                continue;
            }
            if (msg.type == Protocol.MsgType.ERROR) {
                throw new IOException("服务端错误: " + asText(msg.payload));
            }
            if (msg.type == Protocol.MsgType.FULL_SERVER && msg.event == 150) {
                sessionStarted = true;
            }
        }
        if (!sessionStarted) {
            throw new IOException("会话启动超时，未收到 event 150");
        }
    }

    private byte[] waitAudioResult() throws Exception {
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + RESULT_TIMEOUT_SEC * 1000L;
        long lastDataAt = System.currentTimeMillis();
        boolean gotAnyAudio = false;

        while (System.currentTimeMillis() < deadline) {
            Protocol.Message msg = incoming.poll(500, TimeUnit.MILLISECONDS);
            if (msg != null) {
                if (msg.type == Protocol.MsgType.ERROR) {
                    throw new IOException("服务端错误: " + asText(msg.payload));
                }
                if (msg.type == Protocol.MsgType.AUDIO_ONLY_SERVER && msg.payload != null && msg.payload.length > 0) {
                    audio.write(msg.payload);
                    gotAnyAudio = true;
                    lastDataAt = System.currentTimeMillis();
                    continue;
                }
                if (msg.type == Protocol.MsgType.FULL_SERVER && (msg.event == 359 || msg.event == 459)) {
                    if (gotAnyAudio) {
                        // 正常完成：收到语音并且对话返回结束事件。
                        break;
                    }
                }
            }

            if (gotAnyAudio && System.currentTimeMillis() - lastDataAt > AUDIO_IDLE_BREAK_MS) {
                break;
            }
        }

        byte[] pcm = audio.toByteArray();
        if (pcm.length == 0) {
            throw new IOException("未收到端到端语音输出音频");
        }
        return toWav(pcm, 24000, 1, 16);
    }

    private String asText(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "unknown";
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    private byte[] toWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcm.length;
        int chunkSize = 36 + dataSize;

        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.write(intLE(chunkSize));
        out.write("WAVE".getBytes(StandardCharsets.US_ASCII));
        out.write("fmt ".getBytes(StandardCharsets.US_ASCII));
        out.write(intLE(16)); // subchunk1 size
        out.write(shortLE((short) 1)); // PCM
        out.write(shortLE((short) channels));
        out.write(intLE(sampleRate));
        out.write(intLE(byteRate));
        out.write(shortLE((short) blockAlign));
        out.write(shortLE((short) bitsPerSample));
        out.write("data".getBytes(StandardCharsets.US_ASCII));
        out.write(intLE(dataSize));
        out.write(pcm);
        return out.toByteArray();
    }

    private byte[] intLE(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private byte[] shortLE(short value) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array();
    }

    private static class ClientImpl extends WebSocketClient {
        private final BlockingQueue<Protocol.Message> queue;

        ClientImpl(URI uri, Map<String, String> headers, BlockingQueue<Protocol.Message> queue) {
            super(uri, new Draft_6455(), headers, (int) TimeUnit.SECONDS.toMillis(10));
            this.queue = queue;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            log.debug("[VolcanoVoiceOut] WebSocket opened");
        }

        @Override
        public void onMessage(String text) {
            log.debug("[VolcanoVoiceOut] text msg: {}", text != null && text.length() > 200 ? text.substring(0, 200) + "..." : text);
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                byte[] data = new byte[bytes.remaining()];
                bytes.get(data);
                Protocol.Message msg = Protocol.unmarshal(data);
                queue.offer(msg);
            } catch (IOException e) {
                log.warn("[VolcanoVoiceOut] unmarshal: {}", e.getMessage());
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            log.debug("[VolcanoVoiceOut] closed: {} {}", code, reason);
        }

        @Override
        public void onError(Exception ex) {
            log.warn("[VolcanoVoiceOut] error: {}", ex.getMessage());
        }
    }
}
