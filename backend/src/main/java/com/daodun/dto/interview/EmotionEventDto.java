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

    /** 面试语义情绪标签：平稳 / 专注 / 紧张 / 积极 / 波动 */
    private String emotion;

    /** 置信度 0.0~1.0 */
    private double confidence;
}
