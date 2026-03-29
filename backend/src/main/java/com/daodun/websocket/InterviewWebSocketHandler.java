package com.daodun.websocket;

import com.daodun.common.BusinessException;
import com.daodun.config.EmotionProperties;
import com.daodun.config.VoiceProperties;
import com.daodun.dto.interview.PostTurnRequest;
import com.daodun.dto.interview.PostTurnResponse;
import com.daodun.dto.voice.TtsResult;
import com.daodun.dto.voice.VoiceInboundMessage;
import com.daodun.dto.voice.VoiceOutboundMessage;
import com.daodun.service.EmotionAnalysisService;
import com.daodun.service.InterviewService;
import com.daodun.service.VoiceRecognitionService;
import com.daodun.service.VoiceSynthesisService;
import com.daodun.service.model.EmotionAnalysisResult;
import com.daodun.voice.volcano.OpenspeechAsrV2StreamingClient;
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
    private final VoiceProperties voiceProperties;
    private final InterviewService interviewService;
    private final VoiceRecognitionService voiceRecognitionService;
    private final VoiceSynthesisService voiceSynthesisService;
    private final EmotionAnalysisService emotionAnalysisService;
    private final EmotionProperties emotionProperties;

    private final Map<String, ByteArrayOutputStream> audioBuffers = new ConcurrentHashMap<>();
    /** 流式 ASR (api/v2/asr)：按 WebSocket session 维护，边说边上传到识别服务 */
    private final Map<String, OpenspeechAsrV2StreamingClient> asrStreamingClients = new ConcurrentHashMap<>();
    /** 情绪识别节流：按 wsSessionId 记录上次分析时间 */
    private final Map<String, Long> emotionFrameTimestamps = new ConcurrentHashMap<>();

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
            case "emotion_frame" -> onEmotionFrame(session, in);
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
        OpenspeechAsrV2StreamingClient asr = asrStreamingClients.remove(session.getId());
        if (asr != null) asr.close();
        emotionFrameTimestamps.remove(session.getId());
    }

    private void onAudioChunk(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        if (in.getData() == null || in.getData().isBlank()) return;
        byte[] chunk = Base64.getDecoder().decode(in.getData());
        // 无论是否走流式 ASR，都先本地缓存一份，保证流式失败时可回退整段识别。
        ByteArrayOutputStream bos = audioBuffers.computeIfAbsent(session.getId(), k -> new ByteArrayOutputStream());
        bos.write(chunk);

        if (isStreamingAsrEnabled()) {
            // 流式 ASR：边说边上传，每个 chunk 实时发往识别服务
            OpenspeechAsrV2StreamingClient asr = asrStreamingClients.get(session.getId());
            if (asr == null) {
                VoiceProperties.Stt stt = voiceProperties.getStt();
                String appId = (stt.getAppId() != null && !stt.getAppId().isBlank()) ? stt.getAppId() : stt.getAppKey();
                if (appId == null || appId.isBlank() || stt.getApiKey() == null || stt.getApiKey().isBlank()) {
                    log.warn("[Voice] 流式 ASR 未配置 appId/apiKey，降级为缓冲后识别");
                } else {
                    try {
                        String resourceId = (stt.getResourceId() == null || stt.getResourceId().isBlank())
                                ? "volc.asr.default" : stt.getResourceId().trim();
                        String cluster = (stt.getCluster() == null || stt.getCluster().isBlank())
                                ? "volcano_asr" : stt.getCluster().trim();
                        asr = new OpenspeechAsrV2StreamingClient(
                                stt.getEndpoint(),
                                stt.getApiKey(),
                                appId,
                                cluster,
                                resourceId
                        );
                        log.info("[VoiceTrace] 流式 ASR 建连参数 wsSessionId={} endpoint={} appId={} cluster={} resourceId={}",
                                session.getId(), stt.getEndpoint(), appId, cluster, resourceId);
                        asr.connectAndSendConfig();
                        asrStreamingClients.put(session.getId(), asr);
                        log.info("[VoiceTrace] 流式 ASR 已连接，开始边说边传 wsSessionId={}", session.getId());
                    } catch (Exception e) {
                        log.warn("[Voice] 流式 ASR 连接失败，降级为缓冲后识别: {}", e.getMessage());
                    }
                }
            }
            if (asr != null) {
                try {
                    asr.sendAudioChunk(chunk);
                } catch (Exception e) {
                    log.warn("[Voice] 流式 ASR 发送 chunk 失败: {}", e.getMessage());
                }
            }
        }
        log.debug("[VoiceTrace] audio_chunk wsSessionId={} chunkBytes={} finalChunk={} streaming={}",
                session.getId(), chunk.length, Boolean.TRUE.equals(in.getFinalChunk()), isStreamingAsrEnabled());
        if (Boolean.TRUE.equals(in.getFinalChunk())) {
            onAudioCommit(session, in);
        }
    }

    private boolean isStreamingAsrEnabled() {
        String endpoint = voiceProperties.getStt().getEndpoint();
        return endpoint != null && endpoint.contains("api/v2/asr");
    }

    private void onAudioCommit(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        String transcript = null;
        OpenspeechAsrV2StreamingClient asr = asrStreamingClients.remove(session.getId());

        if (isStreamingAsrEnabled()) {
            if (asr == null) {
                send(session, VoiceOutboundMessage.builder()
                        .type("error")
                        .content("流式语音识别未建立连接，请检查 STT endpoint/cluster/resource-id 配置")
                        .isFinal(true)
                        .build());
                return;
            }
            try {
                transcript = asr.sendAudioLastAndGetResult(null);
            } catch (Exception e) {
                log.warn("[Voice] 流式 ASR 异常: {}", e.getMessage());
                send(session, VoiceOutboundMessage.builder()
                        .type("error")
                        .content(e.getMessage() != null ? e.getMessage() : "流式语音识别失败，请检查配置")
                        .isFinal(true)
                        .build());
                return;
            } finally {
                asr.close();
            }
            log.info("[VoiceTrace] audio_commit 流式 ASR 结果 wsSessionId={} appSessionId={} text={}",
                    session.getId(), in.getSessionId(), transcript != null ? transcript : "");
            if (transcript == null || transcript.isBlank()) {
                send(session, VoiceOutboundMessage.builder()
                        .type("error")
                        .content("流式语音识别未返回结果，请检查 resource-id / token 权限 / cluster")
                        .isFinal(true)
                        .build());
                return;
            }
        } else {
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
            var it = resp.getInterviewerTurn();
            String content = it != null && it.getContent() != null ? it.getContent() : "";
            if (content.isBlank()) {
                content = "请继续回答。";
            }
            String coding = it != null ? it.getCodingProblemContent() : null;
            send(session, VoiceOutboundMessage.builder()
                    .type("subtitle")
                    .content(content)
                    .codingProblemContent(coding)
                    .isFinal(true)
                    .build());
            pushTts(session, content, false);
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
        // 获取用户输入的文本内容
        String userText = in.getContent();
        if (userText == null || userText.isBlank()) {
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content("文本内容不能为空，请重新输入")
                    .isFinal(true)
                    .build());
            return;
        }

        log.info("[VoiceTrace] text_answer wsSessionId={} appSessionId={} text={}",
                session.getId(), in.getSessionId(), userText);

        send(session, VoiceOutboundMessage.builder()
                .type("user_transcript")
                .content(userText)
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
        req.setContent(userText);
        if (in.getClientTurnId() != null && !in.getClientTurnId().isBlank()) {
            req.setClientTurnId(in.getClientTurnId());
        }
        try {
            PostTurnResponse resp = interviewService.postTurn(userId, in.getSessionId(), req);
            var it = resp.getInterviewerTurn();
            String content = it != null && it.getContent() != null ? it.getContent() : "";
            if (content.isBlank()) {
                content = "请继续回答。";
            }
            String coding = it != null ? it.getCodingProblemContent() : null;

            // 文本模式下只发送 subtitle（文本内容），不发送音频；算法题干走 codingProblemContent
            log.info("[VoiceTrace] text_mode_reply wsSessionId={} appSessionId={} text={}",
                    session.getId(), in.getSessionId(), content);
            send(session, VoiceOutboundMessage.builder()
                    .type("subtitle")
                    .content(content)
                    .codingProblemContent(coding)
                    .isFinal(true)
                    .build());
        } catch (BusinessException e) {
            log.warn("[VoiceTrace] postTurn 失败 wsSessionId={} appSessionId={}: {}", session.getId(), in.getSessionId(), e.getMessage());
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content(e.getMessage() != null ? e.getMessage() : "AI 面试官暂时无响应，请稍后重试")
                    .isFinal(true)
                    .build());
        }
    }

    private void onPlayWelcome(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null || in.getSessionId() == null) return;
        try {
            String welcomeText = interviewService.generateWelcomeStreaming(userId, in.getSessionId(), delta -> {});
            log.info("[VoiceTrace] welcome_text wsSessionId={} appSessionId={} text={}",
                    session.getId(), in.getSessionId(), welcomeText);

            // 先下发完整字幕（文字侧依赖 isFinal；避免 TTS 再推一条 subtitle 导致重复）
            send(session, VoiceOutboundMessage.builder()
                    .type("subtitle")
                    .content(welcomeText)
                    .isFinal(true)
                    .build());

            // 文字面试（textOnly）：不调用 TTS，不推送 interviewer_audio
            if (!Boolean.TRUE.equals(in.getTextOnly())) {
                pushTts(session, welcomeText, false);
            } else {
                log.info("[VoiceTrace] welcome_text_only wsSessionId={} appSessionId={} (skip TTS)", session.getId(), in.getSessionId());
            }
        } catch (BusinessException e) {
            log.warn("[VoiceTrace] generateWelcomeStreaming 失败 wsSessionId={} appSessionId={}: {}", session.getId(), in.getSessionId(), e.getMessage());
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content(e.getMessage() != null ? e.getMessage() : "AI 面试官暂时无响应，请稍后重试")
                    .isFinal(true)
                    .build());
        }
    }

    private void onEmotionFrame(WebSocketSession session, VoiceInboundMessage in) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null || in.getSessionId() == null) {
            return;
        }
        String imageBase64 = in.getImageBase64();
        if (imageBase64 == null || imageBase64.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastTs = emotionFrameTimestamps.get(session.getId());
        long minIntervalMs = Math.max(200L, emotionProperties.getMinIntervalMs());
        if (lastTs != null && now - lastTs < minIntervalMs) {
            return;
        }
        emotionFrameTimestamps.put(session.getId(), now);
        EmotionAnalysisResult analysis = emotionAnalysisService.analyzeFrame(in.getSessionId(), imageBase64, in.getCapturedAt());
        send(session, VoiceOutboundMessage.builder()
                .type("emotion_status")
                .emotion(analysis.getEmotion())
                .confidence(analysis.getConfidence())
                .hasFace(analysis.getHasFace())
                .capturedAt(analysis.getCapturedAt())
                .status(analysis.getStatus())
                .isFinal(true)
                .build());
    }

    private void pushTts(WebSocketSession session, String reply) throws Exception {
        pushTts(session, reply, true);
    }

    /**
     * @param sendSubtitle 是否推送字幕；开场白若已单独推送过字幕，可传 false 仅拉音频
     */
    private void pushTts(WebSocketSession session, String reply, boolean sendSubtitle) throws Exception {
        log.info("[VoiceTrace] push_tts wsSessionId={} text={} sendSubtitle={}", session.getId(), reply, sendSubtitle);
        TtsResult ttsResult = voiceSynthesisService.synthesize(reply);
        if (sendSubtitle && ttsResult.getSubtitle() != null && !ttsResult.getSubtitle().isBlank()) {
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
        if (sendSubtitle) {
            send(session, VoiceOutboundMessage.builder()
                    .type("error")
                    .content("TTS 未返回音频，请检查语音合成配置")
                    .isFinal(true)
                    .build());
        } else {
            log.warn("[VoiceTrace] TTS 未返回音频（字幕已单独推送，跳过 error） wsSessionId={}", session.getId());
        }
    }

    private void send(WebSocketSession session, VoiceOutboundMessage payload) throws Exception {
        if (!session.isOpen()) return;
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }
}
