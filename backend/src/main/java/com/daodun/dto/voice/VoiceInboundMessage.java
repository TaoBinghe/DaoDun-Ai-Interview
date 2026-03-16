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
    /** 摄像头帧（Base64 或 data URL） */
    private String imageBase64;
    /** 客户端采样时间戳（毫秒） */
    private Long capturedAt;
}
