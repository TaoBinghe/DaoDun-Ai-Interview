package com.daodun.service;

import com.daodun.service.model.EmotionAnalysisResult;

public interface EmotionAnalysisService {

    EmotionAnalysisResult analyzeFrame(Long sessionId, String imageBase64, Long capturedAt);
}
