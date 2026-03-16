package com.daodun.service.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmotionAnalysisResult {

    private String emotion;
    private Double confidence;
    private Boolean hasFace;
    private Long capturedAt;
    private String status;
}
