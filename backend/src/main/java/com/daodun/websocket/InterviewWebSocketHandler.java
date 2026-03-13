package com.daodun.websocket;

import com.daodun.common.BusinessException;
import com.daodun.dto.interview.PostTurnRequest;
import com.daodun.dto.interview.PostTurnResponse;
import com.daodun.dto.voice.TtsResult;
import com.daodun.dto.voice.VoiceInboundMessage;
import com.daodun.dto.voice.VoiceOutboundMessage;
import com.daodun.service.InterviewService;
import com.daodun.service.VoiceRecognitionService;
import com.daodun.service.VoiceSynthesisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final InterviewService interviewService;
    private final VoiceRecognitionService voiceRecognitionService;
    private final VoiceSynthesisService voiceSynthesisService;

    private final Map<String, ByteArrayOutputStream> audioBuffers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        audioBuffers.put(session.getId(), new ByteArrayOutputStream());
        send(session, VoiceOutboundMessage.builder()
                .type("connected")
                .content("语音通道已连接")
                .isFinal(true)
                .build());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        VoiceInboundMessage in = objectMapper.readValue(message.getPayload(), VoiceInboundMessage.class);
        String type = in.getType() == null ? "" : in.getType();
        switch (type) {
            case "audio_chunk" -> onAudioChunk(session, in);
            case "audio_commit" -> onAudioCommit(session, in);
            case "text_answer" -> onTextAnswer(session, in);
            case "play_welcome" -> onPlayWelcome(session, in);
            case "ping" -> send(session, VoiceOutboundMessage.builder().type("pong").isFinal(true).build());
            default -> send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content("不支持的消息类型: " + type)
                    .isFinal(true)
                    .build());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[VoiceTrace] ws_closed wsSessionId={} code={} reason={}",
                session.getId(), status.getCode(), status.getReason());
        audioBuffers.remove(session.getId());
    }

    private void onAudioChunk(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        if (in.getData() == null || in.getData().isBlank()) return;
        ByteArrayOutputStream bos = audioBuffers.computeIfAbsent(session.getId(), k -> new ByteArrayOutputStream());
        byte[] chunk = Base64.getDecoder().decode(in.getData());
        bos.write(chunk);
        log.debug("[VoiceTrace] audio_chunk wsSessionId={} chunkBytes={} finalChunk={}",
                session.getId(), chunk.length, Boolean.TRUE.equals(in.getFinalChunk()));
        if (Boolean.TRUE.equals(in.getFinalChunk())) {
            onAudioCommit(session, in);
        }
    }

    private void onAudioCommit(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        ByteArrayOutputStream bos = audioBuffers.computeIfAbsent(session.getId(), k -> new ByteArrayOutputStream());
        byte[] audioBytes = bos.toByteArray();
        bos.reset();
        log.info("[VoiceTrace] audio_commit wsSessionId={} appSessionId={} audioBytes={} format={}",
                session.getId(), in.getSessionId(), audioBytes.length, in.getFormat());
        if (audioBytes.length == 0) {
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content("未收到语音数据，请重试")
                    .isFinal(true)
                    .build());
            return;
        }
        String format = (in.getFormat() != null && !in.getFormat().isBlank()) ? in.getFormat().trim() : "webm";
        String transcript;
        try {
            transcript = voiceRecognitionService.transcribe(audioBytes, format, 16000);
        } catch (Exception e) {
            log.warn("[Voice] STT 异常，不断开连接: {}", e.getMessage());
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content(e.getMessage() != null ? e.getMessage() : "语音识别失败，请重试或改用文本输入")
                    .isFinal(true)
                    .build());
            return;
        }
        if (transcript == null || transcript.isBlank()) {
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content("未识别到有效内容，请重试")
                    .isFinal(true)
                    .build());
            return;
        }

        log.info("[VoiceTrace] user_transcript wsSessionId={} appSessionId={} text={}",
                session.getId(), in.getSessionId(), transcript);

        send(session, VoiceOutboundMessage.builder()
                .type("user_transcript")
                .content(transcript)
                .isFinal(true)
                .build());

        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null || in.getSessionId() == null) {
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content("会话信息缺失，请重新进入面试")
                    .isFinal(true)
                    .build());
            return;
        }

        PostTurnRequest req = new PostTurnRequest();
        req.setContent(transcript);
        try {
            PostTurnResponse resp = interviewService.postTurn(userId, in.getSessionId(), req);
            String content = resp.getInterviewerTurn() != null && resp.getInterviewerTurn().getContent() != null
                    ? resp.getInterviewerTurn().getContent() : "";
            if (content.isBlank()) {
                content = "请继续回答。";
            }
            pushTts(session, content);
        } catch (BusinessException e) {
            log.warn("[VoiceTrace] postTurn 失败 wsSessionId={} appSessionId={}: {}", session.getId(), in.getSessionId(), e.getMessage());
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content(e.getMessage() != null ? e.getMessage() : "AI 面试官暂时无响应，请稍后重试")
                    .isFinal(true)
                    .build());
        }
    }

    private void onTextAnswer(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        send(session, VoiceOutboundMessage.builder()
                .type("error")
                .content("文本面试模式已关闭，请使用语音作答")
                .isFinal(true)
                .build());
    }

    private void onPlayWelcome(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null || in.getSessionId() == null) return;
        try {
            String welcomeText = interviewService.generateWelcomeStreaming(userId, in.getSessionId(), delta -> {});
            log.info("[VoiceTrace] welcome_text wsSessionId={} appSessionId={} text={}",
                    session.getId(), in.getSessionId(), welcomeText);
            pushTts(session, welcomeText);
        } catch (BusinessException e) {
            log.warn("[VoiceTrace] generateWelcomeStreaming 失败 wsSessionId={} appSessionId={}: {}", session.getId(), in.getSessionId(), e.getMessage());
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content(e.getMessage() != null ? e.getMessage() : "AI 面试官暂时无响应，请稍后重试")
                    .isFinal(true)
                    .build());
        }
    }

    private void pushTts(WebSocketSession session, String reply) throws Exception {
        log.info("[VoiceTrace] push_tts wsSessionId={} text={}", session.getId(), reply);
        TtsResult ttsResult = voiceSynthesisService.synthesize(reply);
        if (ttsResult.getSubtitle() != null && !ttsResult.getSubtitle().isBlank()) {
            send(session, VoiceOutboundMessage.builder()
                    .type("subtitle")
                    .content(ttsResult.getSubtitle())
                    .isFinal(true)
                    .build());
        }
        if (ttsResult.getAudioBase64() != null && !ttsResult.getAudioBase64().isBlank()) {
            send(session, VoiceOutboundMessage.builder()
                    .type("interviewer_audio")
                    .data(ttsResult.getAudioBase64())
                    .mimeType(ttsResult.getMimeType())
                    .isFinal(true)
                    .build());
            return;
        }
        send(session, VoiceOutboundMessage.builder()
                .type("error")
                .content("TTS 未返回音频，请检查语音合成配置")
                .isFinal(true)
                .build());
    }

    private void send(WebSocketSession session, VoiceOutboundMessage payload) throws Exception {
        if (!session.isOpen()) return;
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }
}
