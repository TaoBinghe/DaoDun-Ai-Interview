package com.daodun.repository;

import com.daodun.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByDocumentIdOrderBySourceOrderAsc(Long documentId);

    void deleteByDocumentId(Long documentId);

    /**
     * 基于 pgvector 的向量相似度检索（余弦距离），带岗位过滤。
     * 返回按距离升序排列（最相似在前）。
     */
    @Query(value = """
            SELECT kc.* FROM knowledge_chunks kc
            WHERE kc.position_name = :positionName
              AND kc.embedding IS NOT NULL
            ORDER BY kc.embedding <=> cast(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<KnowledgeChunk> findByVectorSimilarity(
            @Param("positionName") String positionName,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("topK") int topK);

    /**
     * 基于关键词的全文匹配检索（PostgreSQL ILIKE 模式），带岗位过滤。
     */
    @Query(value = """
            SELECT kc.* FROM knowledge_chunks kc
            WHERE kc.position_name = :positionName
              AND (kc.keywords ILIKE '%' || :keyword || '%'
                   OR kc.search_text ILIKE '%' || :keyword || '%')
            ORDER BY kc.source_order
            LIMIT :topK
            """, nativeQuery = true)
    List<KnowledgeChunk> findByKeyword(
            @Param("positionName") String positionName,
            @Param("keyword") String keyword,
            @Param("topK") int topK);

    /**
     * 更新单条 chunk 的 embedding 向量。
     */
    @Modifying
    @Query(value = """
            UPDATE knowledge_chunks SET embedding = cast(:embedding AS vector)
            WHERE id = :chunkId
            """, nativeQuery = true)
    void updateEmbedding(@Param("chunkId") Long chunkId, @Param("embedding") String embedding);

    /**
     * 清空所有 chunk 的 embedding，便于重新生成向量。
     */
    @Modifying
    @Query(value = "UPDATE knowledge_chunks SET embedding = NULL", nativeQuery = true)
    int clearAllEmbeddings();
}
