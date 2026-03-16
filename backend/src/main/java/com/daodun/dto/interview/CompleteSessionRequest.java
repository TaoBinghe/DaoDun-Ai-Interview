package com.daodun.dto.interview;

import lombok.Data;

import java.util.List;

/**
 * 结束面试会话的请求体（可选字段，兼容不携带请求体的旧调用）
 */
@Data
public class CompleteSessionRequest {

    /**
     * 面试全程的情绪时间线（由前端在情绪变化时累积，结束时上报）。
     * 可以为 null 或空列表，后端均能处理。
     */
    private List<EmotionEventDto> emotionTimeline;
}
