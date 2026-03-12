package com.daodun.config;

import com.daodun.entity.KnowledgeChunk;
import com.daodun.repository.KnowledgeChunkRepository;
import com.daodun.service.EmbeddingService;
import com.daodun.service.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class KnowledgeBaseInitializer implements CommandLineRunner {

    private final RagProperties ragProperties;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        if (!ragProperties.isEnabled()) {
            log.info("[KnowledgeInit] RAG 已禁用，跳过知识库初始化");
            return;
        }

        ensurePgvectorExtension();

        int ingested = ingestionService.ingestAll();
        log.info("[KnowledgeInit] 知识入库完成，本次入库 {} 条", ingested);

        if (ingested > 0) {
            generateEmbeddings();
        }
    }

    private void ensurePgvectorExtension() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            log.info("[KnowledgeInit] pgvector 扩展已就绪");
        } catch (Exception e) {
            log.warn("[KnowledgeInit] 创建 pgvector 扩展失败（可能已存在或权限不足）: {}", e.getMessage());
        }
    }

    private void generateEmbeddings() {
        List<KnowledgeChunk> chunks = chunkRepository.findAll().stream()
                .filter(c -> c.getEmbedding() == null || c.getEmbedding().isBlank())
                .toList();

        if (chunks.isEmpty()) {
            log.info("[KnowledgeInit] 所有 chunk 已有 embedding，跳过");
            return;
        }

        log.info("[KnowledgeInit] 开始为 {} 条 chunk 生成 embedding...", chunks.size());

        int batchSize = 10;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<KnowledgeChunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            List<String> texts = batch.stream().map(KnowledgeChunk::getSearchText).toList();

            try {
                List<float[]> embeddings = embeddingService.embedBatch(texts);
                if (embeddings.size() != batch.size()) {
                    log.warn("[KnowledgeInit] embedding 数量不匹配: batchSize={}, embeddingSize={}", batch.size(), embeddings.size());
                }
                for (int j = 0; j < embeddings.size() && j < batch.size(); j++) {
                    String vecStr = EmbeddingService.toVectorString(embeddings.get(j));
                    chunkRepository.updateEmbedding(batch.get(j).getId(), vecStr);
                }
                log.info("[KnowledgeInit] embedding 批次完成 {}/{}", Math.min(i + batchSize, chunks.size()), chunks.size());
            } catch (Exception e) {
                log.error("[KnowledgeInit] embedding 批次失败 (offset={}): {}", i, e.getMessage());
            }
        }

        log.info("[KnowledgeInit] embedding 生成完毕");
    }
}
