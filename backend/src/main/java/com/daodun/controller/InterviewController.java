package com.daodun.controller;

import com.daodun.common.R;
import com.daodun.dto.interview.*;
import com.daodun.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 面试会话接口。所有接口均需携带有效 Authorization: Bearer <accessToken>。
 */
@Slf4j
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * 创建面试会话。
     * POST /api/interview/sessions
     */
    @PostMapping("/sessions")
    public R<SessionDetailResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        Long userId = currentUserId();
        log.info("[InterviewController] createSession userId={} positionId={}", userId, request.getPositionId());
        SessionDetailResponse resp = interviewService.createSession(userId, request);
        return R.ok("面试会话创建成功", resp);
    }

    /**
     * 获取会话详情（含全部对话轮次）。
     * GET /api/interview/sessions/{id}
     */
    @GetMapping("/sessions/{id}")
    public R<SessionDetailResponse> getSessionDetail(@PathVariable Long id) {
        Long userId = currentUserId();
        return R.ok(interviewService.getSessionDetail(userId, id));
    }

    /**
     * 提交用户回答，AI 面试官生成下一句（追问或下一题）。
     * POST /api/interview/sessions/{id}/turns
     */
    @PostMapping("/sessions/{id}/turns")
    public R<PostTurnResponse> postTurn(@PathVariable Long id,
                                        @Valid @RequestBody PostTurnRequest request) {
        Long userId = currentUserId();
        log.info("[InterviewController] postTurn userId={} sessionId={} clientTurnId={}",
                userId, id, request.getClientTurnId());
        PostTurnResponse resp = interviewService.postTurn(userId, id, request);
        return R.ok(resp);
    }

    /**
     * 结束面试会话（IN_PROGRESS -> COMPLETED，幂等）。
     * 可携带情绪时间线，将触发异步AI评估报告生成。
     * PATCH /api/interview/sessions/{id}/complete
     */
    @PatchMapping("/sessions/{id}/complete")
    public R<Void> completeSession(@PathVariable Long id,
                                   @RequestBody(required = false) CompleteSessionRequest request) {
        Long userId = currentUserId();
        log.info("[InterviewController] completeSession userId={} sessionId={} hasTimeline={}",
                userId, id, request != null && request.getEmotionTimeline() != null);
        interviewService.completeSession(userId, id, request);
        return R.ok("面试已结束", null);
    }

    /**
     * 获取面试评估报告。
     * status=GENERATING 表示生成中，前端可每隔3秒轮询；status=READY 表示已就绪。
     * GET /api/interview/sessions/{id}/evaluation
     */
    @GetMapping("/sessions/{id}/evaluation")
    public R<EvaluationReportResponse> getEvaluation(@PathVariable Long id) {
        Long userId = currentUserId();
        log.info("[InterviewController] getEvaluation userId={} sessionId={}", userId, id);
        return R.ok(interviewService.getEvaluation(userId, id));
    }

    /**
     * 查询当前用户的历史面试记录（按开始时间倒序）。
     * GET /api/interview/sessions
     */
    @GetMapping("/sessions")
    public R<List<SessionSummaryResponse>> listSessions() {
        Long userId = currentUserId();
        return R.ok(interviewService.listUserSessions(userId));
    }

    // ─── 辅助方法 ───

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
