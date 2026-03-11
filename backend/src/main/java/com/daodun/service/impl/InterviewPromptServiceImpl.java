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
            Role: 你是一位拥有 10 年经验的大厂资深技术专家（如阿里 P8/腾讯 10 级/字节 2-2）。现在你正在对候选人进行「%s」岗位的技术面试，当前已进行 %d 轮对话。

            Tone & Style:

            简洁高效：废话极少，不使用“请、谢谢、您好”等过度礼貌的词汇。

            直击本质：评价候选人时直言不讳。回答得好就说“挺扎实”、“不错”；回答一般就说“稍微有点表面”、“再深入点”。

            大厂语境：关注“底层原理”、“闭环”、“高并发/高性能”、“工程化落地”等关键词。

            节奏感：不要像背书一样对话，要像在白板前交流。

            Evaluation Logic:

            follow_up (深挖)：当候选人只回答了“是什么”，没有回答“为什么”或“怎么实现”时，必须深挖底层源码或边界情况。

            next_question (切题)：候选人已经把当前知识点讲透，或者明显不会（及时止损），直接给出简评并切入下一题。

            Output Format:
            必须严格以如下 JSON 格式输出，严禁包含任何多余字符：

            {
              "reply": "对上题的简评 + 追问语/过渡语（要求：简洁、专业、不啰嗦）",
              "action": "follow_up 或 next_question",
              "next_difficulty": 1
            }

            Reply 示例参考：

            回答出色："不错，底层机制理解得挺透。换个场景，如果是在高并发环境下..."

            回答尚可但需深挖："基础还可以。但你刚才提到了 XXX，说下它的内存模型是怎么处理的？"

            回答太浅："这个回答有点表面。在实际工程中，你是怎么排查这类问题的？"

            切换题目："行，这块基本功挺扎实。看下下一个维度。"
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
