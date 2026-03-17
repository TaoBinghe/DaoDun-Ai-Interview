package com.daodun.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 面试评估报告响应体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationReportResponse {

    public enum Status {
        /** 评估任务正在生成中 */
        GENERATING,
        /** 评估报告已就绪 */
        READY,
        /** 评估生成失败 */
        FAILED,
        /** 会话尚未结束，无法评估 */
        NOT_STARTED,
        /** 对话/信息过少，无法生成完整报告（仍可查看本页提示） */
        INSUFFICIENT_DATA
    }

    private Long sessionId;
    private Status status;

    /** 提示信息，如 INSUFFICIENT_DATA / FAILED 时给用户的说明 */
    private String message;

    /** 完整报告，仅 status=READY 时非 null */
    private Report report;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Report {
        private int overallScore;
        private String overallComment;
        private KnowledgeAssessment knowledgeAssessment;
        private EmotionAssessment emotionAssessment;
        private Recommendations recommendations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KnowledgeAssessment {
        private List<String> strengths;
        private List<String> weaknesses;
        private List<TopicDetail> topicDetails;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicDetail {
        private String topic;
        /** good / fair / poor */
        private String rating;
        private String comment;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmotionAssessment {
        private String summary;
        private List<String> positives;
        private List<String> issues;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Recommendations {
        private List<String> learning;
        private List<String> emotional;
    }
}
