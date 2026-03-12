package com.daodun.dto.voice;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 端到端实时语音输出诊断结果。
 */
@Data
@Builder
public class VoiceRealtimeDiagnosticResponse {
    private Map<String, Object> config;
    private Boolean success;
    private String speakerUsed;
    private Integer audioBytesLength;
    private String errorMessage;
}
