package com.daodun.service;

import com.daodun.dto.voice.VoiceRealtimeDiagnosticResponse;
import com.daodun.dto.voice.VoiceTtsDiagnosticResponse;

public interface VoiceDiagnosticService {

    VoiceTtsDiagnosticResponse diagnoseTts(String text);

    /**
     * 使用当前配置调用端到端实时语音输出（WebSocket + event 501），用于诊断 speaker/鉴权/连接等问题。
     */
    VoiceRealtimeDiagnosticResponse diagnoseRealtimeVoice(String text);
}
