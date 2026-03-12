package com.daodun.service;

import com.daodun.dto.interview.LlmDecision;
import com.daodun.entity.InterviewTurn;
import com.daodun.entity.KnowledgeChunk;

import java.util.List;
import java.util.Map;

/**
 * 负责面试 Prompt 的组装与 LLM 结构化输出的解析。
 * 把 Prompt 工程与业务编排解耦，便于独立调优。
 */
public interface InterviewPromptService {

    /**
     * 根据岗位名称和当前全部对话轮次，构造发给 LLM 的 messages 列表。
     */
    List<Map<String, String>> buildMessages(String positionName, List<InterviewTurn> turns, String resumeText);

    /**
     * 带 RAG 知识库上下文的构造方法。知识条目将作为参考注入 system prompt。
     */
    List<Map<String, String>> buildMessages(String positionName, List<InterviewTurn> turns,
                                            String resumeText, List<KnowledgeChunk> knowledgeContext);

    /**
     * 解析 LLM 返回的 JSON 字符串为 LlmDecision。
     * 若解析失败，降级为 follow_up 并将原始文本作为 reply，不抛出异常。
     */
    LlmDecision parseLlmResponse(String rawResponse);
}
