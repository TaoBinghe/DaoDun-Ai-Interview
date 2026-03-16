package com.daodun.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "emotion")
public class EmotionProperties {

    private boolean enabled = true;
    /** Python 表情识别服务 URL */
    private String endpoint = "http://localhost:8091/analyze/frame";
    /** 同一 WebSocket 会话最小分析间隔（毫秒） */
    private long minIntervalMs = 800L;
}
