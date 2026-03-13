package com.daodun.websocket;

import com.daodun.dto.interview.SessionDetailResponse;
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
    private final Map<String, Integer> interviewRounds = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        audioBuffers.put(session.getId(), new ByteArrayOutputStream());
        interviewRounds.put(session.getId(), 0);
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
        interviewRounds.remove(session.getId());
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
        if (userId == null || in.getSessionId() == null) {
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content("会话信息缺失，请重新进入面试")
                    .isFinal(true)
                    .build());
            return;
        }
        respondWithRealtimeInterviewer(session, userId, in.getSessionId(), transcript, false);
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
        respondWithRealtimeInterviewer(session, userId, in.getSessionId(), null, true);
    }

    private void pushTts(WebSocketSession session, String reply) throws Exception {
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
                .content("端到端语音模型未返回音频，请检查语音模型配置")
                .isFinal(true)
                .build());
    }

    private void respondWithRealtimeInterviewer(WebSocketSession session,
                                                Long userId,
                                                Long sessionId,
                                                String userTranscript,
                                                boolean welcome) throws Exception {
        SessionDetailResponse detail = interviewService.getSessionDetail(userId, sessionId);
        String positionName = detail.getPositionName() != null ? detail.getPositionName() : "技术";
        int round = interviewRounds.compute(session.getId(), (k, v) -> v == null ? 1 : v + 1);

        String prompt;
        if (welcome) {
            prompt = """
                    你是技术面试官，不是候选人。现在开始一场%s岗位面试。
                    请你只说一句开场白：要求候选人先做自我介绍。
                    严禁自我介绍，严禁说“我叫/我来应聘/我的经历”。
                    """.formatted(positionName);
        } else {
            String safeUserText = (userTranscript == null || userTranscript.isBlank()) ? "（候选人回答较短）" : userTranscript.trim();
            prompt = """
                    你是%s岗位的技术面试官，不是候选人。当前是第%d轮。
                    候选人刚才回答：%s
                    请作为面试官继续面试：先一句简短评价，再提出一个追问或下一题。
                    严禁把自己当候选人，严禁自我介绍，严禁输出“我叫/我来应聘/我的项目经历”。
                    回复保持口语化，40字以内。
                    """.formatted(positionName, round, safeUserText);
        }
        pushTts(session, prompt);
    }

    private void send(WebSocketSession session, VoiceOutboundMessage payload) throws Exception {
        if (!session.isOpen()) return;
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }
}
