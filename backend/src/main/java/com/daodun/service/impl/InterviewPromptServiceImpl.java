package com.daodun.service.impl;

import com.daodun.dto.interview.LlmDecision;
import com.daodun.entity.InterviewTurn;
import com.daodun.service.InterviewPromptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPromptServiceImpl implements InterviewPromptService {

    private final ObjectMapper objectMapper;

    /**
     * 系统提示词模板。LLM 必须以 JSON 格式输出，后端按 action 字段驱动选题逻辑。
     * 当 action=next_question 时，reply 只写过渡语，系统自动追加下一道题目。
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一位资深技术面试官，正在对候选人进行「%s」岗位的技术面试。
            当前已进行 %d 轮对话。

            【输出格式要求】
            你必须严格以如下 JSON 格式输出，不得包含任何其他文字、代码块标记或解释：
            {
              "reply": "你对候选人说的话（包含评价与追问或过渡语，语气自然、专业）",
              "action": "follow_up 或 next_question",
              "next_difficulty": 1
            }

            【action 策略】
            - "follow_up"：继续深挖当前题目，追问更多细节或原理；next_difficulty 填当前难度（无实际效果）
            - "next_question"：结束当前题目，切换下一题；next_difficulty 指定下一题难度（1=简单 2=中等 3=困难）

            【换题时机建议】
            - 候选人回答准确、全面 → 建议 next_question，适当提高难度
            - 候选人基本正确但不够深入 → 建议 follow_up，追问细节或原理
            - 候选人回答模糊或错误 → 建议 follow_up 进行引导，或 next_question 保持简单难度

            【重要】当 action 为 "next_question" 时，reply 只需包含对候选人当前回答的简短点评与过渡语，\
            系统将自动追加下一道题目，请勿自行编造或重复题目内容。
            """;

    @Override
    public List<Map<String, String>> buildMessages(String positionName, List<InterviewTurn> turns) {
        List<Map<String, String>> messages = new ArrayList<>();

        // system message：面试官角色 + 输出规范
        int turnCount = turns.size();
        String systemContent = String.format(SYSTEM_PROMPT_TEMPLATE, positionName, turnCount);
        messages.add(buildMessage("system", systemContent));

        // 历史轮次：截取最近 20 轮，防止超出上下文窗口
        List<InterviewTurn> recent = turns.size() > 20
                ? turns.subList(turns.size() - 20, turns.size())
                : turns;

        for (InterviewTurn turn : recent) {
            String role = turn.getRole() == InterviewTurn.Role.INTERVIEWER ? "assistant" : "user";
            messages.add(buildMessage(role, turn.getContent()));
        }

        return messages;
    }

    @Override
    public LlmDecision parseLlmResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("[Prompt] LLM 返回为空，降级为 follow_up");
            return fallback("（面试官暂时没有回应，请稍后重试）");
        }

        // 尝试提取 JSON 块（防止模型在 JSON 前后加了多余文字）
        String json = extractJson(rawResponse);
        try {
            LlmDecision decision = objectMapper.readValue(json, LlmDecision.class);
            // 校验并兜底关键字段
            if (decision.getReply() == null || decision.getReply().isBlank()) {
                decision.setReply(rawResponse);
            }
            if (!"follow_up".equals(decision.getAction()) && !"next_question".equals(decision.getAction())) {
                log.warn("[Prompt] 非法 action: {}，降级为 follow_up", decision.getAction());
                decision.setAction("follow_up");
            }
            if (decision.getNextDifficulty() == null
                    || decision.getNextDifficulty() < 1
                    || decision.getNextDifficulty() > 3) {
                decision.setNextDifficulty(1);
            }
            return decision;
        } catch (Exception e) {
            log.warn("[Prompt] JSON 解析失败（{}），降级为 follow_up，原始内容：{}", e.getMessage(), rawResponse);
            return fallback(rawResponse);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private LlmDecision fallback(String reply) {
        LlmDecision d = new LlmDecision();
        d.setReply(reply);
        d.setAction("follow_up");
        d.setNextDifficulty(1);
        return d;
    }

    private Map<String, String> buildMessage(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
