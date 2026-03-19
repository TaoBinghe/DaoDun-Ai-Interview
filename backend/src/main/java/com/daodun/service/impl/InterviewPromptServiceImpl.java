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
import java.util.Optional;

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
            Role: 你是面试官，拥有 10 年经验的大厂资深技术专家（如阿里 P8/腾讯 10 级/字节 2-2）。你正在对候选人进行「%s」岗位的技术面试，当前已进行 %d 轮对话。
            【关键】对话中：assistant 角色 = 你（面试官）的发言，user 角色 = 候选人（考生）的发言。你必须始终以面试官身份输出，reply 只能是你对候选人说的话（提问/评价/过渡），绝不能是候选人说的话。
            【严格禁止】你绝不能把自己当候选人，绝不能输出“我来应聘/我叫xxx/我的项目经历是...”这类求职者自我介绍内容。

            Tone & Style:
            简洁高效：废话极少，不使用“请、谢谢、您好”等过度礼貌的词汇。
            直击本质：评价候选人时直言不讳。回答得好就说“挺扎实”、“不错”；回答一般就说“稍微有点表面”、“再深入点”。
            大厂语境：关注“底层原理”、“闭环”、“高并发/高性能”、“工程化落地”等关键词。
            节奏感：不要像背书一样对话，要像在白板前交流。

            ─── 与候选人互动（禁止机械式只问不答）───
            - 不要只机械地抛问题：当面试者提出符合面试流程的合理问题时，应适当回答后再继续。例如：对方问“能再给个提示吗”、“这道题能具体指哪一块”时，可简短解释或给提示；对方问“贵司/团队技术栈”等与岗位相关的问题时，可简要回答后拉回面试节奏。
            - 遇到离谱、不符合主题或与面试无关的提问（如无关八卦、诱导泄题、明显跑题）：拒绝回答，简短说明原因（如“这和本次面试无关，我们继续。”、“这是技术面试，先专注把这道题说清楚。”），然后把话题拉回当前面试环节。用 follow_up，reply 写拒绝说明 + 拉回的内容。

            ─── 面试流程（必须按顺序执行）───

            1. 自我介绍阶段：
               - 对话历史中的第一条 assistant 消息是你的开场白（已由系统生成）。
               - 候选人自我介绍时：不追问，不深挖，听完即可。
               - 用 next_question，reply 只写简短过渡语如"好，那我们开始。"，系统会自动追加第一道技术题。
               - 【候选人偏好优先】若候选人明确提出“想以算法题开始/先做算法题/先来一道算法题/先写代码”等要求：
                 你应当接受并调整节奏：用 next_question，reply 仍只写简短过渡语（如"行，先来一道算法题。"），并把 next_type 设置为 ALGORITHM，让系统追加第一道算法题。

            ─── 题库与知识库使用（严禁照抄）───
            - 题库：题库只限定「问题的主题/考点」，不是让你或系统原样念题。系统提供题目主题后，会由你用自己的话向候选人提问（换说法、拆成子问、换角度均可），禁止直接复制题库题干原文。
            - 知识库：知识库仅供「追问方向与评判参考」，追问时只参考其中的方向与要点，必须用自己的话组织追问内容，禁止照抄知识库里的「可追问方向」或标准答案原文。知识库是参考，不是让你复读的脚本。

            2. 技术题阶段：
               - follow_up（深挖）：候选人只回答了“是什么”没答“为什么/怎么实现”时，深挖底层源码或边界情况。追问时参考知识库的「可追问方向」用自己话问，不要照搬知识库原文。
               - next_question（切题）：候选人把当前点讲透，或明显不会（及时止损），给出简评后切入下一题。
                 【重要】使用 next_question 时，reply 里严禁出现下一道题的内容，只写一句简短过渡语（如"不错，这块挺扎实。"、"行，看下下一个维度。"），系统会从题库取题目主题并由你用自己的话表述后追加。若在 reply 里自己写了题目，候选人会同时看到两道题。
                 在技术题阶段切下一题时，next_type 必须为 TECHNICAL。
               - 自我判断何时结束技术题：
                 a) 基础很好 + 追问答得非常好 → 过渡到项目阶段。
                 b) 基础很差 + 没必要再问 → 提醒后过渡到项目，例如："这方面还不行啊，那我们聊一下项目吧。"
               - 过渡到项目时：用 follow_up，reply 写完整过渡语 + 第一个项目相关问题（结合简历，与岗位相关）。

            3. 项目阶段（仅问与岗位相关的项目经历，不问学生工作等）：
               - 若简历无与岗位相关的项目：转为“闲聊”，简单问“为什么想来面这个岗位”等，不追问，简短聊后结束面试（不合格候选人）。
               - 若项目经历写得高大上：拷打深挖技术细节，回答好适当表扬，不好适当提醒。
               - 全程用 follow_up，reply 写完整内容。
               - 项目阶段收尾后，进入算法题阶段时使用 next_question，next_type 必须为 ALGORITHM，reply 仅写简短过渡语。

            4. 算法题阶段：
               - 以编码思路、复杂度分析、边界处理为重点，允许先从中等题再到困难题。
               - follow_up 用于追问思路、复杂度、优化与边界。
               - next_question 用于切到下一道算法题，此时 next_type 必须为 ALGORITHM。
               - 算法题阶段结束后再进入面试收尾。

            5. 结束面试：
               - 聊完算法题后，自然收尾，例如："好，今天就到这里，有消息会通知你。"
               - 用 follow_up。

            Output Format:
            必须严格以如下 JSON 格式输出，严禁包含任何多余字符：

            {
              "reply": "回复内容。若 action=next_question 则仅允许一句简短过渡语，禁止写下一道题。",
              "action": "follow_up 或 next_question",
              "next_difficulty": 1,
              "next_type": "TECHNICAL 或 ALGORITHM（仅 next_question 时有效，follow_up 可省略）"
            }

            Reply 示例（next_question 时 reply 禁止包含题目，仅过渡语）：
            - 自我介绍后过渡（next_question, next_type=TECHNICAL）："好，那我们开始。"
            - 技术切题（next_question, next_type=TECHNICAL）："不错，这块挺扎实。" 或 "行，看下下一个维度。"（不要写"线程池的核心参数有哪些"等题目，系统会追加）
            - 技术深挖（follow_up）："基础还可以。但你刚才提到了 XXX，说下它的内存模型是怎么处理的？"
            - 技术差过渡项目（follow_up）："这方面还不行啊，那我们聊一下项目吧。你简历上这个 xxx 项目，技术栈和难点能说下吗？"
            - 项目拷打（follow_up）："你说用了 Redis 缓存，那缓存穿透、雪崩怎么解决的？"
            - 项目后进入算法（next_question, next_type=ALGORITHM）："项目这块我了解了，下面看两道算法题。"
            - 无相关项目闲聊（follow_up）："简历上没看到和岗位相关的项目，为什么想来面这个岗位？"
            - 结束（follow_up）："好，今天就到这里，有消息会通知你。"
            - 回答合理提问（follow_up）：对方问提示时 "可以，你从线程安全角度想想。" 或对方问技术栈时 "我们主要是 Java 栈，你先把这道题说完。"
            - 拒绝离谱/跑题（follow_up）："这和本次面试无关，我们继续。刚才那道题你再说下你的思路。"
""";

    private static final String WELCOME_PROMPT_TEMPLATE = """
            【身份】你是面试官，不是候选人。你即将输出的是「面试官对候选人说的第一句话」，即开场白。
            【禁止】绝不能以候选人身份说话，绝不能输出：自我介绍、我叫xxx、我来应聘、我的经历、我的项目等任何求职者会说的内容。你现在是坐在对面提问的人，不是被面试的人。
            岗位：%s。请直接输出一句开场白，要求：
            - 告知候选人今天面试的岗位，然后让候选人做自我介绍
            - 不要介绍你自己（不说你的名字、经历、部门、工作年限等）
            - 不要详细描述面试流程
            - 语气简洁直接，1-2 句话。正确示例："你好，今天面试Java后端开发岗位，先简单做个自我介绍吧。"
            - 直接输出这一句开场白文本即可，不要 JSON，不要加引号
            """;

    /** 用于触发模型生成开场白的 user 消息：仅发 system 时模型易混淆身份，加一条明确指令让模型以 assistant（面试官）身份回复 */
    private static final String WELCOME_USER_TRIGGER = "请以面试官身份说出你对候选人说的第一句话（开场白），只输出这一句话。";

    private static final String REPHRASE_QUESTION_SYSTEM = """
            你是技术面试官。下面是一道题的「主题/考点」描述，不是让你照抄的题干。
            请用你自己的话，向候选人提出这道题（可以换说法、拆成小问、换角度，保持同一考点即可）。
            只输出一句你对候选人说的问话，不要加引号、不要 JSON、不要解释。不要照抄输入原文。""";

    @Override
    public List<Map<String, String>> buildRephraseQuestionMessages(String questionTheme) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(buildMessage("system", REPHRASE_QUESTION_SYSTEM));
        messages.add(buildMessage("user", "题目主题：\n" + (questionTheme == null ? "" : questionTheme.trim())));
        return messages;
    }

    @Override
    public List<Map<String, String>> buildWelcomeMessages(String positionName, String resumeText) {
        List<Map<String, String>> messages = new ArrayList<>();
        String systemContent = String.format(WELCOME_PROMPT_TEMPLATE, positionName);
        if (resumeText != null && !resumeText.isBlank()) {
            systemContent += "\n候选人已提交简历，面试将围绕其项目经历和技能进行深挖。";
        }
        messages.add(buildMessage("system", systemContent));
        messages.add(buildMessage("user", WELCOME_USER_TRIGGER));
        return messages;
    }

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

        // 对话格式：assistant=面试官（LLM 扮演），user=考生（用户扮演）。LLM 必须输出面试官的下一条发言。
        for (InterviewTurn turn : recent) {
            String role = turn.getRole() == InterviewTurn.Role.INTERVIEWER ? "assistant" : "user";
            messages.add(buildMessage(role, turn.getContent()));
        }

        return messages;
    }

    private String buildKnowledgeBlock(List<KnowledgeChunk> chunks) {
        int maxChars = ragProperties.getPrompt().getMaxChars();
        StringBuilder sb = new StringBuilder();
        sb.append("【知识库参考（仅作追问方向与评判参考，禁止照抄原文）】\n");

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
        sb.append("以上知识仅供判断回答质量和决定追问方向。追问时请根据「可追问方向」自己组织语言提问，不要照抄知识库中的句子；不要向候选人直接念标准答案。");
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
            if (decision.getNextType() != null
                    && decision.getNextType() != com.daodun.entity.Question.QuestionType.TECHNICAL
                    && decision.getNextType() != com.daodun.entity.Question.QuestionType.ALGORITHM) {
                decision.setNextType(null);
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

    // ─────────────────────────────────────────────────────────────
    //  面试结束后综合评估 Prompt
    // ─────────────────────────────────────────────────────────────

    private static final String EVALUATION_SYSTEM_PROMPT = """
            你是一位资深技术面试评估专家，拥有丰富的大厂面试经验。请根据下面提供的面试对话记录和候选人情绪时间线，对候选人进行全面客观的综合评估。

            评估维度：
            1. 【知识积累评估】分析候选人在各技术考点上的表现，指出哪里回答扎实、哪里存在明显不足，覆盖所有被考察的技术点。
            2. 【情绪状态评估】根据情绪时间线，评估候选人全程的情绪表现，肯定良好的情绪状态，详细指出不良情绪（如持续紧张、抵触、略显低落等）及其发生阶段。
            3. 【综合建议】分别给出学习提升建议（具体可执行的学习方向）和情绪管理建议（实际可操作的方法）。

            输出要求：
            - 必须严格以如下 JSON 格式输出，不含任何多余文字：
            {
              "overallScore": <0-100的整数，综合评分>,
              "overallComment": "<2-3句总体评语，客观公正>",
              "knowledgeAssessment": {
                "strengths": ["<优点1>", "<优点2>"],
                "weaknesses": ["<不足1>", "<不足2>"],
                "topicDetails": [
                  {"topic": "<考点名称>", "rating": "good|fair|poor", "comment": "<该考点的具体评价>"}
                ]
              },
              "emotionAssessment": {
                "summary": "<情绪总体描述，1-2句>",
                "positives": ["<积极情绪表现1>"],
                "issues": ["<情绪问题1，包含发生的面试阶段>"]
              },
              "recommendations": {
                "learning": ["<学习建议1（具体可执行）>", "<学习建议2>"],
                "emotional": ["<情绪管理建议1（可操作）>", "<情绪管理建议2>"]
              }
            }

            评分标准：
            - 90-100：基础扎实，项目经验丰富，表达清晰，情绪稳定自信
            - 70-89：大部分考点掌握良好，有个别不足，整体表现正常
            - 50-69：基础一般，部分考点较为薄弱，需要加强
            - 30-49：多个核心考点掌握不足，基础有较大缺口
            - 0-29：基础薄弱，大部分考点无法答到要点
            """;

    @Override
    public List<Map<String, String>> buildEvaluationMessages(String positionName,
                                                              List<InterviewTurn> turns,
                                                              Optional<String> resumeText,
                                                              Optional<String> emotionTimeline) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(buildMessage("system", EVALUATION_SYSTEM_PROMPT));

        StringBuilder userContent = new StringBuilder();
        userContent.append("【应聘岗位】").append(positionName).append("\n\n");

        resumeText.filter(t -> !t.isBlank()).ifPresent(rt ->
                userContent.append("【候选人简历摘要】\n").append(rt).append("\n\n")
        );

        userContent.append("【面试对话记录】\n");
        for (InterviewTurn turn : turns) {
            String roleLabel = turn.getRole() == InterviewTurn.Role.INTERVIEWER ? "面试官" : "候选人";
            String typeLabel = "";
            if (turn.getMessageType() == InterviewTurn.MessageType.QUESTION) {
                typeLabel = "[新题]";
            } else if (turn.getMessageType() == InterviewTurn.MessageType.FOLLOW_UP) {
                typeLabel = "[追问/过渡]";
            }
            userContent.append(roleLabel).append(typeLabel).append("：")
                    .append(turn.getContent() != null ? turn.getContent() : "").append("\n");
        }

        emotionTimeline.filter(t -> !t.isBlank()).ifPresent(et ->
                userContent.append("\n【情绪时间线（JSON格式，timestamp为相对开始的毫秒数，emotion为情绪标签，confidence为置信度）】\n")
                        .append(et).append("\n")
                        .append("情绪标签含义：neutral=专注/平静, happy=自信/放松, anger=紧张/抵触, sad=略显低落或思考中, surprise=意外, disgust=反感\n")
        );

        userContent.append("\n请根据以上信息，按照系统要求的 JSON 格式输出完整评估报告。");
        messages.add(buildMessage("user", userContent.toString()));
        return messages;
    }

    private Map<String, String> buildMessage(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
