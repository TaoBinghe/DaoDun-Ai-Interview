package com.daodun.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "knowledge_chunks", indexes = {
        @Index(name = "idx_kc_position", columnList = "position_name"),
        @Index(name = "idx_kc_category", columnList = "category_level1, category_level2"),
        @Index(name = "idx_kc_difficulty", columnList = "difficulty"),
        @Index(name = "idx_kc_doc_id", columnList = "document_id")
})
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "position_name", nullable = false, length = 100)
    private String positionName;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "category_level1", length = 100)
    private String categoryLevel1;

    @Column(name = "category_level2", length = 100)
    private String categoryLevel2;

    @Column(length = 500)
    private String keywords;

    private Integer difficulty;

    @Column(name = "canonical_question", columnDefinition = "TEXT")
    private String canonicalQuestion;

    @Column(name = "answer_key_points", columnDefinition = "TEXT")
    private String answerKeyPoints;

    @Column(name = "example_answer", columnDefinition = "TEXT")
    private String exampleAnswer;

    @Column(name = "follow_ups", columnDefinition = "TEXT")
    private String followUps;

    @Column(name = "scoring_points", columnDefinition = "TEXT")
    private String scoringPoints;

    @Column(columnDefinition = "TEXT")
    private String pitfalls;

    @Column(name = "search_text", columnDefinition = "TEXT", nullable = false)
    private String searchText;

    @Column(name = "source_order")
    private Integer sourceOrder;

    /**
     * pgvector embedding 向量；仅通过 {@link com.daodun.repository.KnowledgeChunkRepository#updateEmbedding} 写入。
     * 设为 insertable=false, updatable=false 避免 JPA 用 varchar 绑定导致类型错误。
     */
    @Column(name = "embedding", columnDefinition = "vector", insertable = false, updatable = false)
    private String embedding;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
