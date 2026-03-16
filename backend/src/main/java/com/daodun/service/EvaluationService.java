package com.daodun.service;

/**
 * 面试评估报告异步生成服务。
 * 调用方无需等待 LLM 完成，结果写入 interview_sessions.evaluation_report。
 */
public interface EvaluationService {

    /**
     * 异步生成面试评估报告并持久化到会话记录。
     * 方法立即返回，不抛出业务异常（失败时将 evaluation_report 设为 FAILED 状态 JSON）。
     *
     * @param sessionId 面试会话 ID
     */
    void generateAsync(Long sessionId);
}
