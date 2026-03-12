package com.daodun.service.impl;

import com.daodun.config.RagProperties;
import com.daodun.dto.interview.LlmDecision;
import com.daodun.entity.InterviewTurn;
import com.daodun.entity.KnowledgeChunk;
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
    private final RagProperties ragProperties;

    /**
     * 系统提示词模板。LLM 必须以 JSON 格式输出，后端按 action 字段驱动选题逻辑。
     * 当 action=next_question 且处于技术题阶段时，reply 只写过渡语，系统自动追加下一道技术题。
     * 其他阶段（自我介绍后过渡、项目、闲聊、结束）一律用 follow_up，reply 写完整内容。
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            Role: 你是一位拥有 10 年经验的大厂资深技术专家（如阿里 P8/腾讯 10 级/字节 2-2）。现在你正在对候选人进行「%s」岗位的技术面试，当前已进行 %d 轮对话。

            Tone & Style:
            简洁高效：废话极少，不使用“请、谢谢、您好”等过度礼貌的词汇。
            直击本质：评价候选人时直言不讳。回答得好就说“挺扎实”、“不错”；回答一般就说“稍微有点表面”、“再深入点”。
            大厂语境：关注“底层原理”、“闭环”、“高并发/高性能”、“工程化落地”等关键词。
            节奏感：不要像背书一样对话，要像在白板前交流。

            ─── 与候选人互动（禁止机械式只问不答）───
            - 不要只机械地抛问题：当面试者提出符合面试流程的合理问题时，应适当回答后再继续。例如：对方问“能再给个提示吗”、“这道题能具体指哪一块”时，可简短解释或给提示；对方问“贵司/团队技术栈”等与岗位相关的问题时，可简要回答后拉回面试节奏。
            - 遇到离谱、不符合主题或与面试无关的提问（如无关八卦、诱导泄题、明显跑题）：拒绝回答，简短说明原因（如“这和本次面试无关，我们继续。”、“这是技术面试，先专注把这道题说清楚。”），然后把话题拉回当前面试环节。用 follow_up，reply 写拒绝说明 + 拉回的内容。

            ─── 面试流程（必须按顺序执行）───

            1. 自我介绍阶段（第 1 轮）：
               - 开场已由系统完成（你好同学，先做个自我介绍吧）。
               - 候选人自我介绍时：不追问，不深挖，听完即可。
               - 用 next_question，reply 只写简短过渡语如"好，那我们开始。"，系统会自动追加第一道技术题。

            2. 技术题阶段：
               - follow_up（深挖）：候选人只回答了“是什么”没答“为什么/怎么实现”时，深挖底层源码或边界情况。
               - next_question（切题）：候选人把当前点讲透，或明显不会（及时止损），给出简评后切入下一题。
                 【重要】使用 next_question 时，reply 里严禁出现下一道题的内容，只写一句简短过渡语（如"不错，这块挺扎实。"、"行，看下下一个维度。"），系统会自动从题库追加下一道技术题。若在 reply 里自己写了题目，候选人会同时看到两道题。
               - 自我判断何时结束技术题：
                 a) 基础很好 + 追问答得非常好 → 过渡到项目阶段。
                 b) 基础很差 + 没必要再问 → 提醒后过渡到项目，例如："这方面还不行啊，那我们聊一下项目吧。"
               - 过渡到项目时：用 follow_up，reply 写完整过渡语 + 第一个项目相关问题（结合简历，与岗位相关）。

            3. 项目阶段（仅问与岗位相关的项目经历，不问学生工作等）：
               - 若简历无与岗位相关的项目：转为“闲聊”，简单问“为什么想来面这个岗位”等，不追问，简短聊后结束面试（不合格候选人）。
               - 若项目经历写得高大上：拷打深挖技术细节，回答好适当表扬，不好适当提醒。
               - 全程用 follow_up，reply 写完整内容。

            4. 结束面试：
               - 聊完项目或闲聊后，自然收尾，例如："好，今天就到这里，有消息会通知你。"
               - 用 follow_up。

            Output Format:
            必须严格以如下 JSON 格式输出，严禁包含任何多余字符：

            {
              "reply": "回复内容。若 action=next_question 则仅允许一句简短过渡语，禁止写下一道题。",
              "action": "follow_up 或 next_question",
              "next_difficulty": 1
            }

            Reply 示例（next_question 时 reply 禁止包含题目，仅过渡语）：
            - 自我介绍后过渡（next_question）："好，那我们开始。"
            - 技术切题（next_question）："不错，这块挺扎实。" 或 "行，看下下一个维度。"（不要写"线程池的核心参数有哪些"等题目，系统会追加）
            - 技术深挖（follow_up）："基础还可以。但你刚才提到了 XXX，说下它的内存模型是怎么处理的？"
            - 技术差过渡项目（follow_up）："这方面还不行啊，那我们聊一下项目吧。你简历上这个 xxx 项目，技术栈和难点能说下吗？"
            - 项目拷打（follow_up）："你说用了 Redis 缓存，那缓存穿透、雪崩怎么解决的？"
            - 无相关项目闲聊（follow_up）："简历上没看到和岗位相关的项目，为什么想来面这个岗位？"
            - 结束（follow_up）："好，今天就到这里，有消息会通知你。"
            - 回答合理提问（follow_up）：对方问提示时 "可以，你从线程安全角度想想。" 或对方问技术栈时 "我们主要是 Java 栈，你先把这道题说完。"
            - 拒绝离谱/跑题（follow_up）："这和本次面试无关，我们继续。刚才那道题你再说下你的思路。"
""";

    @Override
    public List<Map<String, String>> buildMessages(String positionName, List<InterviewTurn> turns, String resumeText) {
        return buildMessages(positionName, turns, resumeText, null);
    }

    @Override
    public List<Map<String, String>> buildMessages(String positionName, List<InterviewTurn> turns,
                                                   String resumeText, List<KnowledgeChunk> knowledgeContext) {
        List<Map<String, String>> messages = new ArrayList<>();

        int turnCount = turns.size();
        String systemContent = String.format(SYSTEM_PROMPT_TEMPLATE, positionName, turnCount);

        if (resumeText != null && !resumeText.isBlank()) {
            systemContent = systemContent + "\n\n"
                    + "【候选人简历（请重点参考）】\n"
                    + resumeText
                    + "\n【/候选人简历】\n"
                    + "请优先围绕简历中的项目经历、技术栈进行深挖追问，挖掘真实能力。";
        }

        if (knowledgeContext != null && !knowledgeContext.isEmpty()) {
            systemContent = systemContent + "\n\n" + buildKnowledgeBlock(knowledgeContext);
        }

        messages.add(buildMessage("system", systemContent));

        List<InterviewTurn> recent = turns.size() > 20
                ? turns.subList(turns.size() - 20, turns.size())
                : turns;

        for (InterviewTurn turn : recent) {
            String role = turn.getRole() == InterviewTurn.Role.INTERVIEWER ? "assistant" : "user";
            messages.add(buildMessage(role, turn.getContent()));
        }

        return messages;
    }

    private String buildKnowledgeBlock(List<KnowledgeChunk> chunks) {
        int maxChars = ragProperties.getPrompt().getMaxChars();
        StringBuilder sb = new StringBuilder();
        sb.append("【知识库参考（用于追问与评判，禁止逐字复读）】\n");

        for (KnowledgeChunk chunk : chunks) {
            StringBuilder entry = new StringBuilder();
            entry.append("▸ 考点：").append(chunk.getTitle()).append("\n");
            if (chunk.getAnswerKeyPoints() != null) {
                String points = chunk.getAnswerKeyPoints();
                if (points.length() > 300) points = points.substring(0, 300) + "…";
                entry.append("  标准要点：").append(points).append("\n");
            }
            if (chunk.getFollowUps() != null) {
                String followUps = chunk.getFollowUps();
                if (followUps.length() > 200) followUps = followUps.substring(0, 200) + "…";
                entry.append("  可追问方向：").append(followUps).append("\n");
            }
            if (chunk.getScoringPoints() != null) {
                String scoring = chunk.getScoringPoints();
                if (scoring.length() > 200) scoring = scoring.substring(0, 200) + "…";
                entry.append("  评分观察点：").append(scoring).append("\n");
            }
            if (chunk.getPitfalls() != null) {
                String pitfalls = chunk.getPitfalls();
                if (pitfalls.length() > 150) pitfalls = pitfalls.substring(0, 150) + "…";
                entry.append("  常见错误：").append(pitfalls).append("\n");
            }

            if (sb.length() + entry.length() > maxChars) break;
            sb.append(entry);
        }

        sb.append("【/知识库参考】\n");
        sb.append("以上知识仅供你判断候选人回答质量和决定追问方向，不要直接告诉候选人标准答案。");
        return sb.toString();
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
