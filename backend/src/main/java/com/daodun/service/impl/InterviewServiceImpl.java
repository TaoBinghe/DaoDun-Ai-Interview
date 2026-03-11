package com.daodun.service.impl;

import com.daodun.common.BusinessException;
import com.daodun.dto.interview.*;
import com.daodun.entity.InterviewSession;
import com.daodun.entity.InterviewTurn;
import com.daodun.entity.Position;
import com.daodun.entity.Question;
import com.daodun.entity.UserResume;
import com.daodun.repository.InterviewSessionRepository;
import com.daodun.repository.InterviewTurnRepository;
import com.daodun.repository.PositionRepository;
import com.daodun.repository.QuestionRepository;
import com.daodun.repository.UserResumeRepository;
import com.daodun.service.ArkChatService;
import com.daodun.service.InterviewPromptService;
import com.daodun.service.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final PositionRepository positionRepository;
    private final QuestionRepository questionRepository;
    private final UserResumeRepository userResumeRepository;
    private final ArkChatService arkChatService;
    private final InterviewPromptService promptService;
    private final SecureRandom secureRandom = new SecureRandom();

    // ─────────────────────────────────────────────────────────────
    //  创建会话
    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SessionDetailResponse createSession(Long userId, CreateSessionRequest request) {
        Position position = requirePosition(request.getPositionId());
        UserResume resume = null;
        if (request.getResumeId() != null) {
            resume = requireUserResume(userId, request.getResumeId());
        }

        // 首轮：固定打招呼 + 自我介绍，不抽题
        String firstContent = "你好同学，先做个自我介绍吧。";
        Question firstQuestion = drawFirstQuestion(request.getPositionId(), request.getType());

        InterviewSession session = InterviewSession.builder()
                .userId(userId)
                .positionId(position.getId())
                .resumeId(resume != null ? resume.getId() : null)
                .status(InterviewSession.Status.IN_PROGRESS)
                .currentTurnIndex(1)
                .lastQuestionId(firstQuestion.getId())
                .build();
        session = sessionRepository.save(session);

        InterviewTurn firstTurn = InterviewTurn.builder()
                .sessionId(session.getId())
                .turnIndex(1)
                .role(InterviewTurn.Role.INTERVIEWER)
                .messageType(InterviewTurn.MessageType.FOLLOW_UP)
                .questionId(null)
                .content(firstContent)
                .build();
        turnRepository.save(firstTurn);

        log.info("[Interview] 创建会话 sessionId={} userId={} positionId={} firstContent=自我介绍",
                session.getId(), userId, position.getId());

        List<TurnDto> turns = List.of(toTurnDto(firstTurn));
        return toDetailResponse(session, position.getName(), resume != null ? resume.getFileName() : null, turns);
    }

    // ─────────────────────────────────────────────────────────────
    //  获取会话详情
    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SessionDetailResponse getSessionDetail(Long userId, Long sessionId) {
        InterviewSession session = requireSession(sessionId);
        requireOwner(session, userId);

        Position position = positionRepository.findById(session.getPositionId()).orElseThrow();
        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnIndexAsc(sessionId);
        List<TurnDto> turnDtos = turns.stream().map(this::toTurnDto).toList();
        String resumeFileName = null;
        if (session.getResumeId() != null) {
            resumeFileName = userResumeRepository.findById(session.getResumeId())
                    .map(UserResume::getFileName)
                    .orElse(null);
        }

        return toDetailResponse(session, position.getName(), resumeFileName, turnDtos);
    }

    // ─────────────────────────────────────────────────────────────
    //  提交用户回答（核心：选题策略 + 多轮 LLM）
    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PostTurnResponse postTurn(Long userId, Long sessionId, PostTurnRequest request) {
        InterviewSession session = requireSession(sessionId);
        requireOwner(session, userId);
        requireInProgress(session);

        // 幂等检查：相同 clientTurnId 直接返回已存在的结果
        if (request.getClientTurnId() != null && !request.getClientTurnId().isBlank()) {
            var existing = turnRepository.findBySessionIdAndClientTurnId(sessionId, request.getClientTurnId());
            if (existing.isPresent()) {
                log.info("[Interview] 幂等命中 sessionId={} clientTurnId={}", sessionId, request.getClientTurnId());
                return buildIdempotentResponse(sessionId, existing.get(), session.getCurrentTurnIndex());
            }
        }

        // 1. 保存用户 turn
        int userTurnIndex = session.getCurrentTurnIndex() + 1;
        InterviewTurn userTurn = InterviewTurn.builder()
                .sessionId(sessionId)
                .turnIndex(userTurnIndex)
                .role(InterviewTurn.Role.USER)
                .messageType(InterviewTurn.MessageType.ANSWER)
                .content(request.getContent())
                .clientTurnId(request.getClientTurnId())
                .build();
        turnRepository.save(userTurn);

        // 2. 加载完整历史（含刚保存的用户 turn）
        List<InterviewTurn> allTurns = turnRepository.findBySessionIdOrderByTurnIndexAsc(sessionId);

        // 3. 获取岗位名称（用于 Prompt）
        Position position = positionRepository.findById(session.getPositionId()).orElseThrow();

        // 4. 调 LLM，记录耗时
        long llmStart = System.currentTimeMillis();
        String rawReply;
        try {
            String resumeText = null;
            if (session.getResumeId() != null) {
                resumeText = userResumeRepository.findByIdAndUserId(session.getResumeId(), userId)
                        .map(this::buildResumeContext)
                        .orElse(null);
            }
            List<Map<String, String>> messages = promptService.buildMessages(position.getName(), allTurns, resumeText);
            rawReply = arkChatService.chatWithMessages(messages);
        } catch (Exception e) {
            log.error("[Interview] LLM 调用失败 sessionId={}: {}", sessionId, e.getMessage());
            throw new BusinessException(500, "AI 面试官暂时无响应，请稍后重试");
        }
        long latencyMs = System.currentTimeMillis() - llmStart;

        // 5. 解析 LLM 决策（含降级）
        LlmDecision decision = promptService.parseLlmResponse(rawReply);
        log.info("[Interview] LLM 决策 sessionId={} action={} nextDifficulty={} latency={}ms",
                sessionId, decision.getAction(), decision.getNextDifficulty(), latencyMs);

        // 6. 选题策略：若 next_question，抽下一道题并拼接到回复末尾
        InterviewTurn.MessageType interviewerType = InterviewTurn.MessageType.FOLLOW_UP;
        Long nextQuestionId = session.getLastQuestionId();
        String interviewerContent = decision.getReply();

        if ("next_question".equals(decision.getAction())) {
            List<Long> usedIds = extractUsedQuestionIds(allTurns);
            Question nextQuestion = drawNextQuestion(session.getPositionId(), decision.getNextDifficulty(), usedIds);
            if (nextQuestion != null) {
                nextQuestionId = nextQuestion.getId();
                interviewerType = InterviewTurn.MessageType.QUESTION;
                // 过渡语 + 新题目内容
                interviewerContent = decision.getReply().isBlank()
                        ? nextQuestion.getContent()
                        : decision.getReply() + "\n\n" + nextQuestion.getContent();
            } else {
                log.warn("[Interview] 无更多可用题目 sessionId={}，保持追问模式", sessionId);
                interviewerType = InterviewTurn.MessageType.FOLLOW_UP;
            }
        }

        // 7. 保存面试官 turn
        int interviewerTurnIndex = userTurnIndex + 1;
        InterviewTurn interviewerTurn = InterviewTurn.builder()
                .sessionId(sessionId)
                .turnIndex(interviewerTurnIndex)
                .role(InterviewTurn.Role.INTERVIEWER)
                .messageType(interviewerType)
                .questionId(nextQuestionId)
                .content(interviewerContent)
                .latencyMs(latencyMs)
                .build();
        turnRepository.save(interviewerTurn);

        // 8. 更新 session：currentTurnIndex + lastQuestionId（含乐观锁保护）
        session.setCurrentTurnIndex(interviewerTurnIndex);
        session.setLastQuestionId(nextQuestionId);
        try {
            sessionRepository.save(session);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("当前会话正在进行中，请勿并发提交，请稍后重试");
        }

        log.info("[Interview] postTurn 完成 sessionId={} userIdx={} interviewerIdx={}",
                sessionId, userTurnIndex, interviewerTurnIndex);

        return PostTurnResponse.builder()
                .userTurn(toTurnDto(userTurn))
                .interviewerTurn(toTurnDto(interviewerTurn))
                .currentTurnIndex(interviewerTurnIndex)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    //  结束面试
    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void completeSession(Long userId, Long sessionId) {
        InterviewSession session = requireSession(sessionId);
        requireOwner(session, userId);

        if (session.getStatus() == InterviewSession.Status.COMPLETED) {
            log.info("[Interview] 会话已结束（幂等）sessionId={}", sessionId);
            return;
        }

        session.setStatus(InterviewSession.Status.COMPLETED);
        session.setEndTime(LocalDateTime.now());
        sessionRepository.save(session);
        log.info("[Interview] 会话结束 sessionId={} userId={}", sessionId, userId);
    }

    // ─────────────────────────────────────────────────────────────
    //  查询用户会话列表
    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> listUserSessions(Long userId) {
        List<InterviewSession> sessions = sessionRepository.findByUserIdOrderByStartTimeDesc(userId);

        // 批量加载 positionId -> name 映射，避免 N+1
        List<Long> positionIds = sessions.stream()
                .map(InterviewSession::getPositionId)
                .distinct()
                .toList();
        Map<Long, String> positionNames = positionRepository.findAllById(positionIds).stream()
                .collect(Collectors.toMap(Position::getId, Position::getName));

        return sessions.stream()
                .map(s -> SessionSummaryResponse.builder()
                        .sessionId(s.getId())
                        .positionId(s.getPositionId())
                        .positionName(positionNames.getOrDefault(s.getPositionId(), "未知岗位"))
                        .status(s.getStatus().name())
                        .currentTurnIndex(s.getCurrentTurnIndex())
                        .startedAt(s.getStartTime())
                        .endedAt(s.getEndTime())
                        .createTime(s.getCreateTime())
                        .build())
                .toList();
    }

    // ─────────────────────────────────────────────────────────────
    //  私有辅助方法
    // ─────────────────────────────────────────────────────────────

    private InterviewSession requireSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("面试会话不存在"));
    }

    private Position requirePosition(Long positionId) {
        return positionRepository.findById(positionId)
                .orElseThrow(() -> new BusinessException("岗位不存在"));
    }

    private void requireOwner(InterviewSession session, Long userId) {
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该面试会话");
        }
    }

    private void requireInProgress(InterviewSession session) {
        if (session.getStatus() != InterviewSession.Status.IN_PROGRESS) {
            throw new BusinessException("该面试会话已结束，无法继续作答");
        }
    }

    private UserResume requireUserResume(Long userId, Long resumeId) {
        return userResumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new BusinessException("简历不存在或无权限使用"));
    }

    private String buildResumeContext(UserResume resume) {
        StringBuilder sb = new StringBuilder();
        sb.append("【原始简历文本】\n")
                .append(resume.getResumeText())
                .append("\n\n");
        if (resume.getProjectSummary() != null && !resume.getProjectSummary().isBlank()) {
            sb.append("【结构化-项目经历】\n").append(resume.getProjectSummary()).append("\n\n");
        }
        if (resume.getSkillsSummary() != null && !resume.getSkillsSummary().isBlank()) {
            sb.append("【结构化-技能】\n").append(resume.getSkillsSummary()).append("\n\n");
        }
        if (resume.getEducationSummary() != null && !resume.getEducationSummary().isBlank()) {
            sb.append("【结构化-教育背景】\n").append(resume.getEducationSummary()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 首问：简单难度（difficulty=1）随机抽 1 题 */
    private Question drawFirstQuestion(Long positionId, Question.QuestionType type) {
        List<Question> pool = type == null
                ? questionRepository.findByPositionIdAndDifficultyOrderBySortOrderAscIdAsc(positionId, 1)
                : questionRepository.findByPositionIdAndTypeAndDifficultyOrderBySortOrderAscIdAsc(positionId, type, 1);
        if (pool.isEmpty()) {
            throw new BusinessException("该岗位暂无简单难度题目，请先完善题库");
        }
        List<Question> mutable = new ArrayList<>(pool);
        Collections.shuffle(mutable, secureRandom);
        return mutable.get(0);
    }

    /**
     * 后续换题：按指定难度随机抽 1 题，并排除已问过的题目。
     * 若指定难度下无新题可用，降级尝试全难度范围；若仍无题则返回 null（调用方降级为追问）。
     */
    private Question drawNextQuestion(Long positionId, Integer difficulty, List<Long> usedIds) {
        List<Question> pool = questionRepository
                .findByPositionIdAndDifficultyOrderBySortOrderAscIdAsc(positionId, difficulty);
        List<Question> available = pool.stream()
                .filter(q -> !usedIds.contains(q.getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (available.isEmpty()) {
            // 降级：尝试全题库（排除已用）
            List<Question> allPool = questionRepository.findByPositionIdOrderBySortOrderAscIdAsc(positionId);
            available = allPool.stream()
                    .filter(q -> !usedIds.contains(q.getId()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (available.isEmpty()) {
            return null;
        }
        Collections.shuffle(available, secureRandom);
        return available.get(0);
    }

    /** 从历史轮次中提取所有已出现的 questionId（用于去重） */
    private List<Long> extractUsedQuestionIds(List<InterviewTurn> turns) {
        return turns.stream()
                .filter(t -> t.getQuestionId() != null)
                .map(InterviewTurn::getQuestionId)
                .distinct()
                .toList();
    }

    /** 幂等命中时，从已存在的用户 turn 往后找对应的面试官 turn 组装响应 */
    private PostTurnResponse buildIdempotentResponse(Long sessionId, InterviewTurn existingUserTurn, int currentTurnIndex) {
        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnIndexAsc(sessionId);
        InterviewTurn interviewerTurn = turns.stream()
                .filter(t -> t.getTurnIndex() == existingUserTurn.getTurnIndex() + 1
                        && t.getRole() == InterviewTurn.Role.INTERVIEWER)
                .findFirst()
                .orElse(null);
        return PostTurnResponse.builder()
                .userTurn(toTurnDto(existingUserTurn))
                .interviewerTurn(interviewerTurn != null ? toTurnDto(interviewerTurn) : null)
                .currentTurnIndex(currentTurnIndex)
                .build();
    }

    private TurnDto toTurnDto(InterviewTurn turn) {
        return TurnDto.builder()
                .id(turn.getId())
                .turnIndex(turn.getTurnIndex())
                .role(turn.getRole())
                .messageType(turn.getMessageType())
                .content(turn.getContent())
                .questionId(turn.getQuestionId())
                .latencyMs(turn.getLatencyMs())
                .createTime(turn.getCreateTime())
                .build();
    }

    private SessionDetailResponse toDetailResponse(InterviewSession session,
                                                    String positionName,
                                                    String resumeFileName,
                                                    List<TurnDto> turns) {
        return SessionDetailResponse.builder()
                .sessionId(session.getId())
                .positionId(session.getPositionId())
                .positionName(positionName)
                .status(session.getStatus().name())
                .currentTurnIndex(session.getCurrentTurnIndex())
                .hasResume(session.getResumeId() != null)
                .resumeFileName(resumeFileName)
                .startedAt(session.getStartTime())
                .endedAt(session.getEndTime())
                .createTime(session.getCreateTime())
                .turns(turns)
                .build();
    }
}
