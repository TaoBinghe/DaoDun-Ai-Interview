package com.daodun.websocket;

import com.daodun.dto.interview.PostTurnRequest;
import com.daodun.dto.interview.PostTurnResponse;
import com.daodun.dto.interview.SessionDetailResponse;
import com.daodun.dto.interview.TurnDto;
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
import java.util.UUID;
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
        audioBuffers.remove(session.getId());
    }

    private void onAudioChunk(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        if (in.getData() == null || in.getData().isBlank()) return;
        ByteArrayOutputStream bos = audioBuffers.computeIfAbsent(session.getId(), k -> new ByteArrayOutputStream());
        byte[] chunk = Base64.getDecoder().decode(in.getData());
        bos.write(chunk);
        if (Boolean.TRUE.equals(in.getFinalChunk())) {
            onAudioCommit(session, in);
        }
    }

    private void onAudioCommit(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        ByteArrayOutputStream bos = audioBuffers.computeIfAbsent(session.getId(), k -> new ByteArrayOutputStream());
        byte[] audioBytes = bos.toByteArray();
        bos.reset();
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

        send(session, VoiceOutboundMessage.builder()
                .type("user_transcript")
                .content(transcript)
                .isFinal(true)
                .build());

        Long userId = (Long) session.getAttributes().get("userId");
        PostTurnRequest req = new PostTurnRequest();
        req.setContent(transcript);
        req.setClientTurnId(in.getClientTurnId() != null ? in.getClientTurnId() : UUID.randomUUID().toString());
        postTurnStreamingAndRespond(session, userId, in.getSessionId(), req);
    }

    private void onTextAnswer(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        if (in.getContent() == null || in.getContent().isBlank()) {
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content("文本回答不能为空")
                    .isFinal(true)
                    .build());
            return;
        }
        Long userId = (Long) session.getAttributes().get("userId");
        PostTurnRequest req = new PostTurnRequest();
        req.setContent(in.getContent().trim());
        req.setClientTurnId(in.getClientTurnId() != null ? in.getClientTurnId() : UUID.randomUUID().toString());
        postTurnStreamingAndRespond(session, userId, in.getSessionId(), req);
    }

    private void onPlayWelcome(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null || in.getSessionId() == null) return;
        SessionDetailResponse detail = interviewService.getSessionDetail(userId, in.getSessionId());
        String firstInterviewerContent = detail.getTurns().stream()
                .filter(t -> t.getRole() != null && "INTERVIEWER".equals(t.getRole().name()))
                .map(TurnDto::getContent)
                .findFirst()
                .orElse(null);
        if (firstInterviewerContent == null || firstInterviewerContent.isBlank()) return;
        send(session, VoiceOutboundMessage.builder()
                .type("interviewer_text")
                .content(firstInterviewerContent)
                .isFinal(true)
                .build());
        pushTts(session, firstInterviewerContent);
    }

    private void pushTts(WebSocketSession session, String reply) throws Exception {
        TtsResult ttsResult = voiceSynthesisService.synthesize(reply);
        send(session, VoiceOutboundMessage.builder()
                .type("subtitle")
                .content(ttsResult.getSubtitle())
                .isFinal(true)
                .build());
        if (ttsResult.getAudioBase64() != null && !ttsResult.getAudioBase64().isBlank()) {
            send(session, VoiceOutboundMessage.builder()
                    .type("interviewer_audio")
                    .data(ttsResult.getAudioBase64())
                    .mimeType(ttsResult.getMimeType())
                    .isFinal(true)
                    .build());
        }
    }

    private void postTurnStreamingAndRespond(WebSocketSession session, Long userId, Long sessionId, PostTurnRequest req) throws Exception {
        PostTurnResponse response;
        try {
            response = interviewService.postTurnStreaming(userId, sessionId, req, delta -> {
                if (delta == null || delta.isBlank()) {
                    return;
                }
                try {
                    send(session, VoiceOutboundMessage.builder()
                            .type("interviewer_text_delta")
                            .content(delta)
                            .isFinal(false)
                            .build());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }

        String reply = response.getInterviewerTurn() != null ? response.getInterviewerTurn().getContent() : "";
        send(session, VoiceOutboundMessage.builder()
                .type("interviewer_text")
                .content(reply)
                .isFinal(true)
                .build());
        pushTts(session, reply);
    }

    private void send(WebSocketSession session, VoiceOutboundMessage payload) throws Exception {
        if (!session.isOpen()) return;
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }
}
