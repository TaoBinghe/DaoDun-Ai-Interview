package com.daodun.service;

/**
 * 火山引擎方舟大模型对话（直接 HTTP 调用，避免 Spring AI 与方舟响应格式差异）
 */
public interface ArkChatService {

    /**
     * 发送一条用户消息，返回模型回复文本
     */
    String chat(String userMessage);
}
