package com.daodun.controller;

import com.daodun.common.R;
import com.daodun.dto.KnowledgeChunkVerifyItem;
import com.daodun.dto.KnowledgeVerifyRequest;
import com.daodun.entity.KnowledgeChunk;
import com.daodun.repository.KnowledgeChunkRepository;
import com.daodun.service.EmbeddingService;
import com.daodun.service.KnowledgeIngestionService;
import com.daodun.service.KnowledgeRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    @PostMapping("/rebuild")
    @Transactional
    public R<Map<String, Object>> rebuild() {
        int count = ingestionService.ingestAll();
        generateEmbeddingsForNew();
        return R.ok(Map.of("ingestedChunks", count, "message", "知识库重建完成"));
    }

    /**
     * 清空所有 chunk 的向量，便于后续重新生成。调用后需再调 rebuild 或重新入库才会生成新向量。
     */
    @PostMapping("/clear-embeddings")
    @Transactional
    public R<Map<String, Object>> clearEmbeddings() {
        int updated = chunkRepository.clearAllEmbeddings();
        log.info("[Knowledge] 已清空 {} 条 chunk 的 embedding", updated);
        return R.ok(Map.of("clearedCount", updated, "message", "已清空全部向量"));
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        long totalChunks = chunkRepository.count();
        long withEmbedding = chunkRepository.findAll().stream()
                .filter(c -> c.getEmbedding() != null && !c.getEmbedding().isBlank())
                .count();
        return R.ok(Map.of(
                "totalChunks", totalChunks,
                "withEmbedding", withEmbedding,
                "withoutEmbedding", totalChunks - withEmbedding
        ));
    }

    /**
     * 知识库检索效果验证：用与面试相同的混合检索逻辑，返回命中的 chunk 列表，便于核对 RAG 是否命中预期条目。
     */
    @PostMapping("/verify")
    public R<List<KnowledgeChunkVerifyItem>> verify(@RequestBody KnowledgeVerifyRequest request) {
        if (request.getPositionName() == null || request.getPositionName().isBlank()) {
            return R.fail("岗位名称不能为空");
        }
        String queryText = request.getQueryText() != null ? request.getQueryText() : "";
        List<String> keywords = request.getKeywords() != null ? request.getKeywords() : List.of();
        List<KnowledgeChunk> chunks = knowledgeRetrievalService.retrieve(
                request.getPositionName().trim(), queryText, keywords);
        List<KnowledgeChunkVerifyItem> items = new ArrayList<>();
        for (KnowledgeChunk c : chunks) {
            items.add(KnowledgeChunkVerifyItem.builder()
                    .id(c.getId())
                    .documentId(c.getDocumentId())
                    .title(c.getTitle())
                    .categoryLevel1(c.getCategoryLevel1())
                    .categoryLevel2(c.getCategoryLevel2())
                    .keywords(c.getKeywords())
                    .difficulty(c.getDifficulty())
                    .canonicalQuestion(c.getCanonicalQuestion())
                    .answerKeyPoints(c.getAnswerKeyPoints())
                    .exampleAnswer(c.getExampleAnswer())
                    .followUps(c.getFollowUps())
                    .scoringPoints(c.getScoringPoints())
                    .pitfalls(c.getPitfalls())
                    .sourceOrder(c.getSourceOrder())
                    .build());
        }
        return R.ok(items);
    }

    private void generateEmbeddingsForNew() {
        List<KnowledgeChunk> needEmbed = chunkRepository.findAll().stream()
                .filter(c -> c.getEmbedding() == null || c.getEmbedding().isBlank())
                .toList();
        if (needEmbed.isEmpty()) return;

        int batchSize = 10;
        for (int i = 0; i < needEmbed.size(); i += batchSize) {
            List<KnowledgeChunk> batch = needEmbed.subList(i, Math.min(i + batchSize, needEmbed.size()));
            List<String> texts = batch.stream().map(KnowledgeChunk::getSearchText).toList();
            try {
                List<float[]> embeddings = embeddingService.embedBatch(texts);
                if (embeddings.size() != batch.size()) {
                    log.warn("[Knowledge] embedding 数量不匹配: batchSize={}, embeddingSize={}", batch.size(), embeddings.size());
                }
                for (int j = 0; j < embeddings.size() && j < batch.size(); j++) {
                    String vecStr = EmbeddingService.toVectorString(embeddings.get(j));
                    chunkRepository.updateEmbedding(batch.get(j).getId(), vecStr);
                }
            } catch (Exception e) {
                log.error("[Knowledge] embedding 批次失败: {}", e.getMessage());
            }
        }
    }
}
