package com.daodun.dto.voice;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VoiceOutboundMessage {

    private String type;
    private String content;
    /** 与 subtitle 同条消息下发：算法题完整题干，供前端「代码编写」面板展示 */
    private String codingProblemContent;
    private String data;
    private String mimeType;
    private Boolean isFinal;
    private String emotion;
    private Double confidence;
    private Boolean hasFace;
    private Long capturedAt;
    private String status;
}
