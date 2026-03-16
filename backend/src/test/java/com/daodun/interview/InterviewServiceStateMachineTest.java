package com.daodun.interview;

import com.daodun.common.BusinessException;
import com.daodun.dto.interview.CreateSessionRequest;
import com.daodun.dto.interview.PostTurnRequest;
import com.daodun.entity.*;
import com.daodun.repository.*;
import com.daodun.config.RagProperties;
import com.daodun.service.ArkChatService;
import com.daodun.service.EvaluationService;
import com.daodun.service.InterviewPromptService;
import com.daodun.service.KnowledgeRetrievalService;
import com.daodun.service.impl.InterviewServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InterviewService 状态机与安全校验单元测试。
 * 使用 Mockito 隔离外部依赖（DB、LLM）。
 */
@ExtendWith(MockitoExtension.class)
class InterviewServiceStateMachineTest {

    @Mock private InterviewSessionRepository sessionRepository;
    @Mock private InterviewTurnRepository turnRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private UserResumeRepository userResumeRepository;
    @Mock private ArkChatService arkChatService;
    @Mock private InterviewPromptService promptService;
    @Mock private KnowledgeRetrievalService knowledgeRetrievalService;
    @Mock private RagProperties ragProperties;
    @Mock private EvaluationService evaluationService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private InterviewServiceImpl interviewService;

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final Long SESSION_ID = 10L;
    private static final Long POSITION_ID = 1L;

    private Position mockPosition;

    @BeforeEach
    void setUp() {
        mockPosition = Position.builder()
                .id(POSITION_ID).name("Java后端").sortOrder(0).build();
    }

    // ─── 状态机：已结束会话不可继续回复 ─────────────────────────

    @Test
    @DisplayName("postTurn：COMPLETED 状态下抛出 BusinessException")
    void postTurn_completedSession_throwsException() {
        InterviewSession completed = buildSession(USER_A, InterviewSession.Status.COMPLETED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(completed));

        PostTurnRequest req = new PostTurnRequest();
        req.setContent("我的回答");

        assertThatThrownBy(() -> interviewService.postTurn(USER_A, SESSION_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束");
    }

    @Test
    @DisplayName("completeSession：COMPLETED 状态重复调用幂等，不抛异常")
    void completeSession_alreadyCompleted_idempotent() {
        InterviewSession completed = buildSession(USER_A, InterviewSession.Status.COMPLETED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(completed));

        // 不应抛异常
        interviewService.completeSession(USER_A, SESSION_ID, null);
        // save 不应被调用（因为已结束）
        verify(sessionRepository, never()).save(any());
    }

    // ─── 越权：B 用户不可访问 A 的会话 ─────────────────────────

    @Test
    @DisplayName("getSessionDetail：越权访问抛出 403 BusinessException")
    void getSessionDetail_unauthorized_throwsException() {
        InterviewSession sessionOfA = buildSession(USER_A, InterviewSession.Status.IN_PROGRESS);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionOfA));

        assertThatThrownBy(() -> interviewService.getSessionDetail(USER_B, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assert be.getCode() == 403;
                });
    }

    @Test
    @DisplayName("postTurn：越权访问抛出 403 BusinessException")
    void postTurn_unauthorized_throwsException() {
        InterviewSession sessionOfA = buildSession(USER_A, InterviewSession.Status.IN_PROGRESS);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionOfA));

        PostTurnRequest req = new PostTurnRequest();
        req.setContent("我的回答");

        assertThatThrownBy(() -> interviewService.postTurn(USER_B, SESSION_ID, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assert be.getCode() == 403;
                });
    }

    @Test
    @DisplayName("completeSession：越权访问抛出 403 BusinessException")
    void completeSession_unauthorized_throwsException() {
        InterviewSession sessionOfA = buildSession(USER_A, InterviewSession.Status.IN_PROGRESS);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionOfA));

        assertThatThrownBy(() -> interviewService.completeSession(USER_B, SESSION_ID, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assert be.getCode() == 403;
                });
    }

    // ─── 幂等：相同 clientTurnId 不重复写入 ─────────────────────

    @Test
    @DisplayName("postTurn：相同 clientTurnId 命中幂等，不重复调用 LLM 和写 turn")
    void postTurn_idempotent_sameClientTurnId() {
        InterviewSession session = buildSession(USER_A, InterviewSession.Status.IN_PROGRESS);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        InterviewTurn existingUserTurn = InterviewTurn.builder()
                .id(200L).sessionId(SESSION_ID).turnIndex(2)
                .role(InterviewTurn.Role.USER)
                .messageType(InterviewTurn.MessageType.ANSWER)
                .content("已有回答").clientTurnId("idem-key-001").build();

        when(turnRepository.findBySessionIdAndClientTurnId(SESSION_ID, "idem-key-001"))
                .thenReturn(Optional.of(existingUserTurn));
        when(turnRepository.findBySessionIdOrderByTurnIndexAsc(SESSION_ID))
                .thenReturn(List.of(existingUserTurn));

        PostTurnRequest req = new PostTurnRequest();
        req.setContent("重复提交");
        req.setClientTurnId("idem-key-001");

        interviewService.postTurn(USER_A, SESSION_ID, req);

        // LLM 不应被调用
        verify(arkChatService, never()).chatWithMessages(any());
        // 不应新增 turn
        verify(turnRepository, never()).save(any());
    }

    // ─── 创建会话：岗位不存在时抛出异常 ─────────────────────────

    @Test
    @DisplayName("createSession：岗位不存在时抛出 BusinessException")
    void createSession_positionNotFound_throwsException() {
        when(positionRepository.findById(anyLong())).thenReturn(Optional.empty());

        CreateSessionRequest req = new CreateSessionRequest();
        req.setPositionId(999L);

        assertThatThrownBy(() -> interviewService.createSession(USER_A, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("岗位不存在");
    }

    @Test
    @DisplayName("createSession：该岗位无简单题时抛出 BusinessException")
    void createSession_noEasyQuestions_throwsException() {
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(mockPosition));
        when(questionRepository.findByPositionIdAndDifficultyOrderBySortOrderAscIdAsc(POSITION_ID, 1))
                .thenReturn(List.of());

        CreateSessionRequest req = new CreateSessionRequest();
        req.setPositionId(POSITION_ID);

        assertThatThrownBy(() -> interviewService.createSession(USER_A, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简单难度题目");
    }

    // ─── 会话不存在 ──────────────────────────────────────────────

    @Test
    @DisplayName("getSessionDetail：会话不存在时抛出 BusinessException")
    void getSessionDetail_sessionNotFound_throwsException() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interviewService.getSessionDetail(USER_A, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("面试会话不存在");
    }

    // ─── 工具方法 ─────────────────────────────────────────────────

    private InterviewSession buildSession(Long userId, InterviewSession.Status status) {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .userId(userId)
                .positionId(POSITION_ID)
                .status(status)
                .currentTurnIndex(1)
                .lastQuestionId(100L)
                .version(0L)
                .build();
    }
}
