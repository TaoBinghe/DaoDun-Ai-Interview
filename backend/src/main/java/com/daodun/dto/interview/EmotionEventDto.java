package com.daodun.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端上报的单条情绪事件（情绪发生变化时记录）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmotionEventDto {

    /** 相对面试开始的毫秒数 */
    private long timestamp;

    /** 情绪标签，如 neutral/happy/angry 等 */
    private String emotion;

    /** 置信度 0.0~1.0 */
    private double confidence;
}
