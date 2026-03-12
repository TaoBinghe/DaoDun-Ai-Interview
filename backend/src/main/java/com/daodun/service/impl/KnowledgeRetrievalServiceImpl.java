package com.daodun.service.impl;

import com.daodun.config.RagProperties;
import com.daodun.entity.KnowledgeChunk;
import com.daodun.repository.KnowledgeChunkRepository;
import com.daodun.service.EmbeddingService;
import com.daodun.service.KnowledgeRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;

    @Override
    public List<KnowledgeChunk> retrieve(String positionName, String queryText, List<String> keywords) {
        int topK = ragProperties.getRetrieval().getTopK();
        Map<Long, KnowledgeChunk> merged = new LinkedHashMap<>();

        // 第一路：向量召回
        try {
            float[] queryVec = embeddingService.embed(queryText);
            if (queryVec.length > 0) {
                String vecStr = EmbeddingService.toVectorString(queryVec);
                List<KnowledgeChunk> vectorResults =
                        chunkRepository.findByVectorSimilarity(positionName, vecStr, topK);
                for (KnowledgeChunk chunk : vectorResults) {
                    merged.putIfAbsent(chunk.getId(), chunk);
                }
                log.info("[RAG] 向量召回 {} 条, positionName={}", vectorResults.size(), positionName);
            }
        } catch (Exception e) {
            log.warn("[RAG] 向量召回失败，降级为关键词模式: {}", e.getMessage());
        }

        // 第二路：关键词/规则兜底
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw == null || kw.isBlank()) continue;
                try {
                    List<KnowledgeChunk> kwResults =
                            chunkRepository.findByKeyword(positionName, kw.trim(), topK);
                    for (KnowledgeChunk chunk : kwResults) {
                        merged.putIfAbsent(chunk.getId(), chunk);
                    }
                } catch (Exception e) {
                    log.warn("[RAG] 关键词检索失败 keyword={}: {}", kw, e.getMessage());
                }
            }
        }

        List<KnowledgeChunk> result = new ArrayList<>(merged.values());
        if (result.size() > topK) {
            result = result.subList(0, topK);
        }
        log.info("[RAG] 混合检索最终返回 {} 条, positionName={}", result.size(), positionName);
        return result;
    }
}
