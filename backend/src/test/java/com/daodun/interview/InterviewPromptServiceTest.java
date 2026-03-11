package com.daodun.interview;

import com.daodun.dto.interview.LlmDecision;
import com.daodun.entity.InterviewTurn;
import com.daodun.service.impl.InterviewPromptServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InterviewPromptService 单元测试：验证 Prompt 组装与 LLM 响应解析的健壮性。
 */
class InterviewPromptServiceTest {

    private InterviewPromptServiceImpl promptService;

    @BeforeEach
    void setUp() {
        promptService = new InterviewPromptServiceImpl(new ObjectMapper());
    }

    // ─── buildMessages ───────────────────────────────────────────

    @Test
    @DisplayName("buildMessages：空历史时只含 system message")
    void buildMessages_emptyTurns() {
        List<Map<String, String>> messages = promptService.buildMessages("Java后端", List.of(), null);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(messages.get(0).get("content")).contains("Java后端");
    }

    @Test
    @DisplayName("buildMessages：INTERVIEWER turn 映射为 assistant role")
    void buildMessages_interviewerRole() {
        InterviewTurn t = buildTurn(InterviewTurn.Role.INTERVIEWER, "请介绍一下 JVM 内存模型");
        List<Map<String, String>> messages = promptService.buildMessages("Java后端", List.of(t), null);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).get("role")).isEqualTo("assistant");
    }

    @Test
    @DisplayName("buildMessages：USER turn 映射为 user role")
    void buildMessages_userRole() {
        InterviewTurn t = buildTurn(InterviewTurn.Role.USER, "JVM 分为堆、栈、方法区...");
        List<Map<String, String>> messages = promptService.buildMessages("Java后端", List.of(t), null);
        assertThat(messages.get(1).get("role")).isEqualTo("user");
    }

    @Test
    @DisplayName("buildMessages：超过 20 轮时只保留最近 20 轮")
    void buildMessages_truncatesHistory() {
        List<InterviewTurn> turns = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            turns.add(buildTurn(InterviewTurn.Role.USER, "answer " + i));
        }
        List<Map<String, String>> messages = promptService.buildMessages("Java后端", turns, null);
        // system(1) + 最近20条 = 21
        assertThat(messages).hasSize(21);
    }

    @Test
    @DisplayName("buildMessages：传入简历文本时写入 system prompt")
    void buildMessages_withResumeText() {
        List<Map<String, String>> messages = promptService.buildMessages("Java后端", List.of(), "姓名：张三\n技能：Java");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("content")).contains("候选人简历");
        assertThat(messages.get(0).get("content")).contains("技能：Java");
    }

    // ─── parseLlmResponse ────────────────────────────────────────

    @Test
    @DisplayName("parseLlmResponse：正常 JSON 解析成功")
    void parseLlmResponse_validJson() {
        String json = "{\"reply\":\"Great answer!\",\"action\":\"next_question\",\"next_difficulty\":2}";
        LlmDecision d = promptService.parseLlmResponse(json);
        assertThat(d.getReply()).isEqualTo("Great answer!");
        assertThat(d.getAction()).isEqualTo("next_question");
        assertThat(d.getNextDifficulty()).isEqualTo(2);
    }

    @Test
    @DisplayName("parseLlmResponse：JSON 前后有多余文字时也能提取")
    void parseLlmResponse_jsonWithExtraText() {
        String raw = "Here is my reply: {\"reply\":\"Follow up now\",\"action\":\"follow_up\",\"next_difficulty\":1} end.";
        LlmDecision d = promptService.parseLlmResponse(raw);
        assertThat(d.getAction()).isEqualTo("follow_up");
        assertThat(d.getReply()).isEqualTo("Follow up now");
    }

    @Test
    @DisplayName("parseLlmResponse：非法 action 降级为 follow_up")
    void parseLlmResponse_invalidAction() {
        String json = "{\"reply\":\"好的\",\"action\":\"unknown_action\",\"next_difficulty\":1}";
        LlmDecision d = promptService.parseLlmResponse(json);
        assertThat(d.getAction()).isEqualTo("follow_up");
    }

    @Test
    @DisplayName("parseLlmResponse：非法 nextDifficulty 降级为 1")
    void parseLlmResponse_invalidDifficulty() {
        String json = "{\"reply\":\"好的\",\"action\":\"next_question\",\"next_difficulty\":99}";
        LlmDecision d = promptService.parseLlmResponse(json);
        assertThat(d.getNextDifficulty()).isEqualTo(1);
    }

    @Test
    @DisplayName("parseLlmResponse：完全非 JSON 时降级为 follow_up，reply 为原始内容")
    void parseLlmResponse_notJson() {
        String raw = "你好，这是纯文本回复。";
        LlmDecision d = promptService.parseLlmResponse(raw);
        assertThat(d.getAction()).isEqualTo("follow_up");
        assertThat(d.getReply()).isEqualTo(raw);
    }

    @Test
    @DisplayName("parseLlmResponse：空字符串时优雅降级，不抛异常")
    void parseLlmResponse_empty() {
        LlmDecision d = promptService.parseLlmResponse("");
        assertThat(d).isNotNull();
        assertThat(d.getAction()).isEqualTo("follow_up");
    }

    @Test
    @DisplayName("parseLlmResponse：null 时优雅降级，不抛异常")
    void parseLlmResponse_null() {
        LlmDecision d = promptService.parseLlmResponse(null);
        assertThat(d).isNotNull();
        assertThat(d.getAction()).isEqualTo("follow_up");
    }

    // ─── 工具方法 ─────────────────────────────────────────────────

    private InterviewTurn buildTurn(InterviewTurn.Role role, String content) {
        return InterviewTurn.builder()
                .sessionId(1L)
                .turnIndex(1)
                .role(role)
                .content(content)
                .build();
    }
}
