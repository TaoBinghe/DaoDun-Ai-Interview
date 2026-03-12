package com.daodun.controller;

import com.daodun.common.R;
import com.daodun.dto.voice.VoiceRealtimeDiagnosticResponse;
import com.daodun.dto.voice.VoiceTtsDiagnosticRequest;
import com.daodun.dto.voice.VoiceTtsDiagnosticResponse;
import com.daodun.service.VoiceDiagnosticService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/voice/diagnostics")
@RequiredArgsConstructor
public class VoiceDiagnosticController {

    private final VoiceDiagnosticService voiceDiagnosticService;

    @PostMapping("/tts")
    public R<VoiceTtsDiagnosticResponse> diagnoseTts(@RequestBody(required = false) VoiceTtsDiagnosticRequest request) {
        String text = request != null ? request.getText() : null;
        return R.ok(voiceDiagnosticService.diagnoseTts(text));
    }

    /**
     * 端到端实时语音输出诊断：用当前配置的 voice.stt.endpoint + voice.tts.speaker 调一次合成，
     * 返回是否成功、使用的 speaker、错误信息等，便于排查「speaker id invalid」等问题。
     */
    @PostMapping("/realtime-voice")
    public R<VoiceRealtimeDiagnosticResponse> diagnoseRealtimeVoice(@RequestBody(required = false) VoiceTtsDiagnosticRequest request) {
        String text = request != null ? request.getText() : null;
        return R.ok(voiceDiagnosticService.diagnoseRealtimeVoice(text));
    }
}
