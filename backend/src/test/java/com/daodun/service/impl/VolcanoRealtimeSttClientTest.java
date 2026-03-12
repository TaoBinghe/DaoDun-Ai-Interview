package com.daodun.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VolcanoRealtimeSttClientTest {

    @Test
    @DisplayName("重复返回同一句时只保留一份")
    void mergeTranscriptCandidate_keepsSingleCopy() {
        String merged = VolcanoRealtimeSttClient.mergeTranscriptCandidate("你好", "你好你好");

        assertThat(merged).isEqualTo("你好");
    }

    @Test
    @DisplayName("增量更完整的句子会覆盖较短候选")
    void mergeTranscriptCandidate_prefersMoreCompleteCandidate() {
        String merged = VolcanoRealtimeSttClient.mergeTranscriptCandidate("你好", "你好，我先做个自我介绍");

        assertThat(merged).isEqualTo("你好，我先做个自我介绍");
    }

    @Test
    @DisplayName("完整句子中的正常重复内容不会被误删")
    void cleanupTranscript_keepsNaturalSentence() {
        String cleaned = VolcanoRealtimeSttClient.cleanupTranscript("你好你好，我想先介绍一下自己");

        assertThat(cleaned).isEqualTo("你好你好，我想先介绍一下自己");
    }
}
