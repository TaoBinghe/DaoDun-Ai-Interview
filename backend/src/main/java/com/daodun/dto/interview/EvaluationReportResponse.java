package com.daodun.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    /** 会话元信息：岗位名称 */
    private String positionName;
    /** 会话元信息：面试开始时间 */
    private LocalDateTime startTime;
    /** 会话元信息：面试结束时间 */
    private LocalDateTime endTime;

    /** 完整报告，仅 status=READY 时非 null */
    private Report report;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Report {
        private int overallScore;
        private String overallComment;
        /** 评价等级：优秀 / 良好 / 合格 / 待提升 / 不合格 */
        private String ratingLevel;
        private KnowledgeAssessment knowledgeAssessment;
        private EmotionAssessment emotionAssessment;
        private Recommendations recommendations;
        /** 六维综合能力评分（雷达图数据） */
        private AbilityScores abilityScores;
        /** 逐题解析记录 */
        private List<QuestionAnalysis> questionAnalysis;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AbilityScores {
        /** 表达能力 0-100 */
        private int expressionAbility;
        /** 应变能力 0-100 */
        private int adaptability;
        /** 应答能力 0-100 */
        private int responseSpeed;
        /** 逻辑能力 0-100 */
        private int logicAbility;
        /** 专业知识 0-100 */
        private int professionalKnowledge;
        /** 技术深度 0-100 */
        private int technicalDepth;
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
        /** 0-10 数值评分 */
        private int score;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionAnalysis {
        /** 题目序号（从1开始） */
        private int questionIndex;
        /** 题目原题 */
        private String questionContent;
        /** 追问内容列表 */
        private List<String> followUpContents;
        /** 面试者回答摘要 */
        private String candidateAnswer;
        /** 回答优点 */
        private List<String> strengths;
        /** 待改进 */
        private List<String> improvements;
        /** 面试官点评 */
        private String interviewerComment;
        /** 解题思路 */
        private String solutionApproach;
        /** 参考答案 */
        private String referenceAnswer;
    }
}
