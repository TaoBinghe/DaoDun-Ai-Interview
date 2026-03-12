package com.daodun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库检索验证接口返回的单条 chunk 摘要（不含 embedding）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkVerifyItem {

    private Long id;
    private Long documentId;
    private String title;
    private String categoryLevel1;
    private String categoryLevel2;
    private String keywords;
    private Integer difficulty;
    private String canonicalQuestion;
    private String answerKeyPoints;
    private String exampleAnswer;
    private String followUps;
    private String scoringPoints;
    private String pitfalls;
    private Integer sourceOrder;
}
