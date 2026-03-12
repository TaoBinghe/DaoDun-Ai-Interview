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
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private static final Set<String> STOP_WORDS = Set.of(
            "的", "是", "有", "在", "和", "与", "或", "及", "等", "什么", "怎么", "如何", "哪些", "哪个",
            "为什么", "能不能", "可以", "能否", "区别", "关系", "一样", "不同", "相同"
    );

    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;

    @Override
    public List<KnowledgeChunk> retrieve(String positionName, String queryText, List<String> keywords) {
        int topK = ragProperties.getRetrieval().getTopK();
        Map<Long, KnowledgeChunk> merged = new LinkedHashMap<>();

        if (keywords == null || keywords.isEmpty()) {
            keywords = extractKeywordsFromQuery(queryText);
        }

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

        // 第二路：关键词/规则兜底（含从查询中自动提取的关键词）
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

    /**
     * 从查询文本中切出可作为关键词的短语（用于未传 keywords 时补全关键词检索，提高命中率）。
     */
    private List<String> extractKeywordsFromQuery(String queryText) {
        if (queryText == null || queryText.isBlank()) return List.of();
        String normalized = queryText.replaceAll("[？?！!，,；;、\\s]+", " ").trim();
        if (normalized.isEmpty()) return List.of();
        List<String> keywords = Stream.of(normalized.split("[和与、的\\s]+"))
                .map(String::trim)
                .filter(s -> s.length() >= 2 && !STOP_WORDS.contains(s))
                .distinct()
                .limit(5)
                .toList();
        if (!keywords.isEmpty()) {
            log.debug("[RAG] 从查询自动提取关键词: {}", keywords);
        }
        return keywords;
    }
}
