package com.daodun.service;

import com.daodun.dto.interview.CreateSessionRequest;
import com.daodun.dto.interview.PostTurnRequest;
import com.daodun.dto.interview.PostTurnResponse;
import com.daodun.dto.interview.SessionDetailResponse;
import com.daodun.dto.interview.SessionSummaryResponse;

import java.util.List;
import java.util.function.Consumer;

/**
 * 面试会话核心业务接口。
 * 严格执行：状态机、owner 校验、选题策略、多轮对话编排。
 */
public interface InterviewService {

    /**
     * 创建面试会话。
     * 首问由选题策略固定为简单难度（difficulty=1）随机抽取，不由调用方指定。
     */
    SessionDetailResponse createSession(Long userId, CreateSessionRequest request);

    /**
     * 获取会话详情（含全部对话轮次）。
     * 仅会话所有者可访问。
     */
    SessionDetailResponse getSessionDetail(Long userId, Long sessionId);

    /**
     * 提交用户回答，触发 LLM 生成面试官下一句（追问或下一题）。
     * 支持 clientTurnId 幂等：重复提交同一 clientTurnId 直接返回上次结果。
     * 仅 IN_PROGRESS 状态的会话可接受回复。
     */
    PostTurnResponse postTurn(Long userId, Long sessionId, PostTurnRequest request);

    /**
     * 提交用户回答并流式返回面试官文本增量。
     */
    PostTurnResponse postTurnStreaming(Long userId, Long sessionId, PostTurnRequest request, Consumer<String> onDelta);

    /**
     * 生成面试开场白（由 LLM 以面试官身份生成）。
     * 若会话已有面试官发言则直接返回已有内容（幂等）。
     * 流式回调 onDelta 用于实时推送文本增量。
     */
    String generateWelcomeStreaming(Long userId, Long sessionId, Consumer<String> onDelta);

    /**
     * 结束面试会话（状态机：IN_PROGRESS -> COMPLETED）。
     * 幂等：已结束的会话重复调用返回成功。
     */
    void completeSession(Long userId, Long sessionId);

    /**
     * 查询当前用户的所有会话摘要（按开始时间倒序）。
     */
    List<SessionSummaryResponse> listUserSessions(Long userId);
}
