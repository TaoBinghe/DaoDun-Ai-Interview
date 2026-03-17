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
        log.info("[RAG][知识库调用] ---------- 知识库检索被调用 ---------- positionName={} queryText长度={} 关键词数={}",
                positionName, queryText == null ? 0 : queryText.length(), keywords == null ? 0 : keywords.size());

        int topK = ragProperties.getRetrieval().getTopK();
        double minScore = ragProperties.getRetrieval().getMinScore();
        Map<Long, KnowledgeChunk> merged = new LinkedHashMap<>();

        log.info("[RAG][检索] 入参 positionName={} topK={} minScore={} queryText(截断)={} keywords={}",
                positionName, topK, minScore, truncate(queryText, 200), keywords);

        if (keywords == null || keywords.isEmpty()) {
            keywords = extractKeywordsFromQuery(queryText);
            log.info("[RAG][检索] 未传关键词，从查询自动提取: {}", keywords);
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
                log.info("[RAG][检索] 向量召回 共 {} 条 (维度={})", vectorResults.size(), queryVec.length);
                for (int i = 0; i < vectorResults.size(); i++) {
                    KnowledgeChunk c = vectorResults.get(i);
                    log.info("[RAG][检索]   向量[{}] id={} title={}", i + 1, c.getId(), truncate(c.getTitle(), 60));
                }
            } else {
                log.warn("[RAG][检索] 向量为空，跳过向量召回");
            }
        } catch (Exception e) {
            log.warn("[RAG][检索] 向量召回失败，降级为关键词模式: {}", e.getMessage());
        }

        // 第二路：关键词/规则兜底（含从查询中自动提取的关键词）
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw == null || kw.isBlank()) continue;
                try {
                    List<KnowledgeChunk> kwResults =
                            chunkRepository.findByKeyword(positionName, kw.trim(), topK);
                    int added = 0;
                    for (KnowledgeChunk chunk : kwResults) {
                        if (merged.putIfAbsent(chunk.getId(), chunk) == null) added++;
                    }
                    log.info("[RAG][检索] 关键词 \"{}\" 命中 {} 条，其中新增 {} 条", kw, kwResults.size(), added);
                } catch (Exception e) {
                    log.warn("[RAG][检索] 关键词检索失败 keyword={}: {}", kw, e.getMessage());
                }
            }
        }

        List<KnowledgeChunk> result = new ArrayList<>(merged.values());
        if (result.size() > topK) {
            result = result.subList(0, topK);
            log.info("[RAG][检索] 合并后超过 topK，截断为 {} 条", topK);
        }
        log.info("[RAG][检索] 混合检索结束 最终返回 {} 条 | id列表={}",
                result.size(), result.stream().map(KnowledgeChunk::getId).toList());
        log.info("[RAG][知识库调用] ---------- 知识库检索结束 返回 {} 条 ----------", result.size());
        return result;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
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
        return keywords;
    }
}
