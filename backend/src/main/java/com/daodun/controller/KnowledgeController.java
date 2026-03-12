package com.daodun.controller;

import com.daodun.common.R;
import com.daodun.entity.KnowledgeChunk;
import com.daodun.repository.KnowledgeChunkRepository;
import com.daodun.service.EmbeddingService;
import com.daodun.service.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/rebuild")
    @Transactional
    public R<Map<String, Object>> rebuild() {
        int count = ingestionService.ingestAll();
        generateEmbeddingsForNew();
        return R.ok(Map.of("ingestedChunks", count, "message", "知识库重建完成"));
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
