package com.daodun.service.impl;

import com.daodun.entity.InterviewSession;
import com.daodun.entity.InterviewTurn;
import com.daodun.entity.Position;
import com.daodun.entity.UserResume;
import com.daodun.repository.InterviewSessionRepository;
import com.daodun.repository.InterviewTurnRepository;
import com.daodun.repository.PositionRepository;
import com.daodun.repository.UserResumeRepository;
import com.daodun.service.ArkChatService;
import com.daodun.service.EvaluationService;
import com.daodun.service.InterviewPromptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final PositionRepository positionRepository;
    private final UserResumeRepository userResumeRepository;
    private final ArkChatService arkChatService;
    private final InterviewPromptService promptService;
    private final ObjectMapper objectMapper;

    /** 标记评估正在生成中的占位 JSON */
    private static final String STATUS_GENERATING = "{\"status\":\"GENERATING\"}";
    /** 标记评估生成失败的占位 JSON */
    private static final String STATUS_FAILED = "{\"status\":\"FAILED\"}";
    /** 对话过少无法生成报告时的占位 JSON（含提示信息） */
    private static final String STATUS_INSUFFICIENT_DATA =
            "{\"status\":\"INSUFFICIENT_DATA\",\"message\":\"本次面试对话轮次过少，无法生成完整评估报告。建议至少进行几轮问答后再结束面试。\"}";

    /**
     * 仅更新 session 的 evaluation_report 字段。每次更新前重新加载 session 以拿到最新 version，
     * 避免与 completeSession 等其它事务的乐观锁冲突。
     */
    private void updateSessionReport(Long sessionId, String reportJson) {
        InterviewSession s = sessionRepository.findById(sessionId).orElse(null);
        if (s == null) return;
        s.setEvaluationReport(reportJson);
        sessionRepository.save(s);
    }

    @Async
    @Override
    @Transactional
    public void generateAsync(Long sessionId) {
        log.info("[Evaluation] 开始异步生成评估报告 sessionId={}", sessionId);

        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("[Evaluation] 会话不存在 sessionId={}", sessionId);
            return;
        }

        // 写入 GENERATING 占位（重新加载再保存，避免乐观锁冲突）
        updateSessionReport(sessionId, STATUS_GENERATING);

        try {
            Position position = positionRepository.findById(session.getPositionId()).orElseThrow();
            List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnIndexAsc(sessionId);

            if (turns.isEmpty()) {
                log.warn("[Evaluation] 会话无对话记录，标记为信息不足 sessionId={}", sessionId);
                updateSessionReport(sessionId, STATUS_INSUFFICIENT_DATA);
                return;
            }

            Optional<String> resumeText = Optional.empty();
            if (session.getResumeId() != null) {
                resumeText = userResumeRepository.findById(session.getResumeId())
                        .map(this::buildResumeContext);
            }

            Optional<String> emotionTimeline = Optional.ofNullable(session.getEmotionTimeline())
                    .filter(t -> !t.isBlank());

            List<Map<String, String>> messages = promptService.buildEvaluationMessages(
                    position.getName(), turns, resumeText, emotionTimeline);

            log.info("[Evaluation] 调用LLM生成评估 sessionId={} turns={}", sessionId, turns.size());
            String rawReport = arkChatService.chatWithMessages(messages);
            log.info("[Evaluation] LLM返回原始评估内容 sessionId={} length={}", sessionId,
                    rawReport != null ? rawReport.length() : 0);

            String reportJson = extractAndValidateJson(rawReport, sessionId);
            updateSessionReport(sessionId, reportJson);
            log.info("[Evaluation] 评估报告生成并存储完成 sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("[Evaluation] 生成评估报告异常 sessionId={}: {}", sessionId, e.getMessage(), e);
            try {
                updateSessionReport(sessionId, STATUS_FAILED);
            } catch (Exception ex) {
                log.error("[Evaluation] 存储FAILED状态也失败 sessionId={}: {}", sessionId, ex.getMessage());
            }
        }
    }

    /**
     * 从 LLM 输出中提取 JSON 部分，并验证其合法性。
     * 若提取或验证失败，返回 STATUS_FAILED。
     */
    private String extractAndValidateJson(String rawOutput, Long sessionId) {
        if (rawOutput == null || rawOutput.isBlank()) {
            log.warn("[Evaluation] LLM返回为空 sessionId={}", sessionId);
            return STATUS_FAILED;
        }
        // 提取 JSON 块（LLM 可能在前后附加多余文字）
        int start = rawOutput.indexOf('{');
        int end = rawOutput.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("[Evaluation] LLM返回不含JSON块 sessionId={} raw={}", sessionId,
                    rawOutput.substring(0, Math.min(200, rawOutput.length())));
            return STATUS_FAILED;
        }
        String json = rawOutput.substring(start, end + 1);
        // 验证 JSON 合法性
        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception e) {
            log.warn("[Evaluation] JSON解析失败 sessionId={}: {}", sessionId, e.getMessage());
            return STATUS_FAILED;
        }
    }

    private String buildResumeContext(UserResume resume) {
        StringBuilder sb = new StringBuilder();
        if (resume.getSkillsSummary() != null && !resume.getSkillsSummary().isBlank()) {
            sb.append("技能：").append(resume.getSkillsSummary()).append("\n");
        }
        if (resume.getProjectSummary() != null && !resume.getProjectSummary().isBlank()) {
            sb.append("项目经历：").append(resume.getProjectSummary()).append("\n");
        }
        if (resume.getEducationSummary() != null && !resume.getEducationSummary().isBlank()) {
            sb.append("教育背景：").append(resume.getEducationSummary());
        }
        return sb.toString().trim();
    }
}
