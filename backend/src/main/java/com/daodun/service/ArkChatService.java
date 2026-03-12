package com.daodun.service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 火山引擎方舟大模型对话（直接 HTTP 调用，避免 Spring AI 与方舟响应格式差异）
 */
public interface ArkChatService {

    /**
     * 单轮对话：发送一条用户消息，返回模型回复文本
     */
    String chat(String userMessage);

    /**
     * 多轮对话：传入完整 messages 列表（含 system/assistant/user），返回模型回复文本。
     * messages 中每个 Map 需包含 "role" 和 "content" 字段。
     */
    String chatWithMessages(List<Map<String, String>> messages);

    /**
     * 多轮对话流式输出：在生成过程中通过 onDelta 推送增量文本，返回最终完整文本。
     */
    String chatWithMessagesStream(List<Map<String, String>> messages, Consumer<String> onDelta);
}
