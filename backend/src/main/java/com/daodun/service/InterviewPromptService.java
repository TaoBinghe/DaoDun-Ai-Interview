package com.daodun.service;

import com.daodun.dto.interview.LlmDecision;
import com.daodun.entity.InterviewTurn;

import java.util.List;
import java.util.Map;

/**
 * 负责面试 Prompt 的组装与 LLM 结构化输出的解析。
 * 把 Prompt 工程与业务编排解耦，便于独立调优。
 */
public interface InterviewPromptService {

    /**
     * 根据岗位名称和当前全部对话轮次，构造发给 LLM 的 messages 列表。
     *
     * @param positionName 岗位名称
     * @param turns        按 turnIndex 升序排列的所有轮次（包含刚保存的用户 turn）
     * @param resumeText   候选人简历文本（可为空）
     * @return OpenAI 格式的 messages（含 system message）
     */
    List<Map<String, String>> buildMessages(String positionName, List<InterviewTurn> turns, String resumeText);

    /**
     * 解析 LLM 返回的 JSON 字符串为 LlmDecision。
     * 若解析失败，降级为 follow_up 并将原始文本作为 reply，不抛出异常。
     *
     * @param rawResponse LLM 原始回复
     * @return 解析结果，永远不为 null
     */
    LlmDecision parseLlmResponse(String rawResponse);
}
