package com.daodun.dto.voice;

import lombok.Data;

@Data
public class VoiceInboundMessage {

    private String type;
    private Long sessionId;
    private String data;
    private String content;
    private String clientTurnId;
    private Boolean finalChunk;
    /** 音频格式：pcm 或 webm，用于 STT 时选择解码方式 */
    private String format;
}
