package com.daodun.voice.volcano;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 火山引擎端到端实时语音二进制协议（与 realtime_dialog 官方 Java 示例一致）。
 */
public final class Protocol {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum MsgType {
        INVALID(0),
        FULL_CLIENT(1),
        AUDIO_ONLY_CLIENT(2),
        FULL_SERVER(9),
        AUDIO_ONLY_SERVER(11),
        FRONT_END_RESULT_SERVER(12),
        ERROR(15);

        private final int value;

        MsgType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static MsgType fromBits(int bits) {
            for (MsgType type : values()) {
                if (type.value == bits) return type;
            }
            return INVALID;
        }
    }

    public static final int MSG_TYPE_FLAG_WITH_EVENT = 0b100;
    public static final int VERSION_1 = 0x10;
    public static final int HEADER_SIZE_4 = 0x1;
    public static final int SERIALIZATION_RAW = 0;
    public static final int SERIALIZATION_JSON = 0b1 << 4;
    public static final int COMPRESSION_NONE = 0;

    public static class Message {
        public MsgType type = MsgType.INVALID;
        public int typeFlag;
        public int event;
        public String sessionId;
        public String connectId;
        public int sequence;
        public long errorCode;
        public byte[] payload;
    }

    public static byte[] marshal(Message msg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(VERSION_1 | HEADER_SIZE_4);
        dos.writeByte((msg.type.getValue() << 4) | (msg.typeFlag & 0x0F));
        dos.writeByte(SERIALIZATION_JSON | COMPRESSION_NONE);
        dos.writeByte(0);
        if ((msg.typeFlag & MSG_TYPE_FLAG_WITH_EVENT) == MSG_TYPE_FLAG_WITH_EVENT) {
            dos.writeInt(msg.event);
        }
        if (shouldWriteSessionId(msg)) {
            byte[] b = msg.sessionId.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(b.length);
            dos.write(b);
        }
        if (msg.payload != null) {
            dos.writeInt(msg.payload.length);
            dos.write(msg.payload);
        } else {
            dos.writeInt(0);
        }
        return baos.toByteArray();
    }

    public static Message unmarshal(byte[] data) throws IOException {
        if (data.length < 4) throw new IOException("数据长度不足");
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        Message msg = new Message();
        buf.get();
        int typeAndFlag = buf.get() & 0xFF;
        buf.get();
        buf.get();
        msg.type = MsgType.fromBits((typeAndFlag >> 4) & 0x0F);
        msg.typeFlag = typeAndFlag & 0x0F;
        if ((msg.typeFlag & MSG_TYPE_FLAG_WITH_EVENT) == MSG_TYPE_FLAG_WITH_EVENT) {
            msg.event = buf.getInt();
        }
        if (shouldReadSessionId(msg)) {
            int size = buf.getInt();
            if (size > 0) {
                byte[] b = new byte[size];
                buf.get(b);
                msg.sessionId = new String(b, StandardCharsets.UTF_8);
            }
        }
        if (msg.type == MsgType.ERROR) {
            msg.errorCode = buf.getInt() & 0xFFFFFFFFL;
        }
        int payloadLen = buf.getInt();
        if (payloadLen > 0) {
            msg.payload = new byte[payloadLen];
            buf.get(msg.payload);
        }
        return msg;
    }

    private static boolean shouldWriteSessionId(Message msg) {
        return (msg.typeFlag & MSG_TYPE_FLAG_WITH_EVENT) == MSG_TYPE_FLAG_WITH_EVENT
                && msg.event != 1 && msg.event != 2 && msg.event != 50 && msg.event != 51 && msg.event != 52;
    }

    private static boolean shouldReadSessionId(Message msg) {
        return (msg.typeFlag & MSG_TYPE_FLAG_WITH_EVENT) == MSG_TYPE_FLAG_WITH_EVENT
                && msg.event != 1 && msg.event != 2 && msg.event != 50 && msg.event != 51 && msg.event != 52;
    }

    public static byte[] createStartConnectionMessage() throws IOException {
        Message msg = new Message();
        msg.type = MsgType.FULL_CLIENT;
        msg.typeFlag = MSG_TYPE_FLAG_WITH_EVENT;
        msg.event = 1;
        msg.payload = "{}".getBytes(StandardCharsets.UTF_8);
        return marshal(msg);
    }

    public static byte[] createStartSessionMessage(String sessionId, String payload) throws IOException {
        Message msg = new Message();
        msg.type = MsgType.FULL_CLIENT;
        msg.typeFlag = MSG_TYPE_FLAG_WITH_EVENT;
        msg.event = 100;
        msg.sessionId = sessionId;
        msg.payload = payload.getBytes(StandardCharsets.UTF_8);
        return marshal(msg);
    }

    public static byte[] createAudioMessage(String sessionId, byte[] audioData) throws IOException {
        Message msg = new Message();
        msg.type = MsgType.AUDIO_ONLY_CLIENT;
        msg.typeFlag = MSG_TYPE_FLAG_WITH_EVENT;
        msg.event = 200;
        msg.sessionId = sessionId;
        msg.payload = audioData;
        return marshalRawAudio(msg);
    }

    public static byte[] createChatTextQueryMessage(String sessionId, String content) throws IOException {
        Message msg = new Message();
        msg.type = MsgType.FULL_CLIENT;
        msg.typeFlag = MSG_TYPE_FLAG_WITH_EVENT;
        msg.event = 501;
        msg.sessionId = sessionId;
        msg.payload = MAPPER.writeValueAsBytes(java.util.Map.of("content", content == null ? "" : content));
        return marshal(msg);
    }

    private static byte[] marshalRawAudio(Message message) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(VERSION_1 | HEADER_SIZE_4);
        dos.writeByte((message.type.getValue() << 4) | (message.typeFlag & 0x0F));
        dos.writeByte(SERIALIZATION_RAW | COMPRESSION_NONE);
        dos.writeByte(0);
        dos.writeInt(message.event);
        byte[] sessionIdBytes = message.sessionId.getBytes(StandardCharsets.UTF_8);
        dos.writeInt(sessionIdBytes.length);
        dos.write(sessionIdBytes);
        if (message.payload != null) {
            dos.writeInt(message.payload.length);
            dos.write(message.payload);
        } else {
            dos.writeInt(0);
        }
        return baos.toByteArray();
    }

    public static String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    private Protocol() {}
}
