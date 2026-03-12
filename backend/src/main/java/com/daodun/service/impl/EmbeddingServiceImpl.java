package com.daodun.service.impl;

import com.daodun.config.RagProperties;
import com.daodun.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    @Value("${spring.ai.openai.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    private final RagProperties ragProperties;

    @Override
    public float[] embed(String text) {
        List<float[]> result = embedBatch(List.of(text));
        return result.isEmpty() ? new float[0] : result.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        String model = ragProperties.getEmbedding().getModel();
        if (model == null || model.isBlank()) {
            log.warn("[Embedding] 未配置 rag.embedding.model（火山方舟需填推理接入点 ID ep-xxxxx），跳过 embedding");
            return List.of();
        }
        boolean useMultimodal = Boolean.TRUE.equals(ragProperties.getEmbedding().getUseMultimodalEndpoint());
        if (!useMultimodal && model.contains("vision")) {
            log.warn("[Embedding] 检测到多模态模型(含 vision)，自动改用多模态接口仅文本调用");
            useMultimodal = true;
        }

        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        long start = System.currentTimeMillis();
        try {
            List<float[]> results;
            if (useMultimodal) {
                results = embedBatchWithMultimodal(client, model, texts);
            } else {
                results = embedBatchWithTextEndpoint(client, model, texts);
            }
            log.info("[Embedding] 调用成功, {} 条文本, 返回 {} 个向量, 耗时 {}ms{}",
                    texts.size(),
                    results.size(),
                    System.currentTimeMillis() - start,
                    useMultimodal ? " (多模态接口仅文本)" : "");
            return results;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (!useMultimodal && msg.contains("400") && (msg.contains("does not support this api") || msg.contains("vision"))) {
                log.warn("[Embedding] 文本接口不支持当前模型，自动改用多模态接口仅文本重试");
                try {
                    List<float[]> results = embedBatchWithMultimodal(client, model, texts);
                    log.info("[Embedding] 调用成功, {} 条文本, 返回 {} 个向量, 耗时 {}ms (多模态接口仅文本)",
                            texts.size(), results.size(), System.currentTimeMillis() - start);
                    return results;
                } catch (Exception fallbackException) {
                    msg = fallbackException.getMessage() != null ? fallbackException.getMessage() : "";
                }
            }

            if (msg.contains("404") || msg.contains("NotFound")) {
                log.error("[Embedding] 接口 404：请将 rag.embedding.model 配置为火山方舟控制台中的推理接入点 ID (ep-xxxxx)。");
            } else if (msg.contains("400") && (msg.contains("does not support this api") || msg.contains("vision"))) {
                log.error("[Embedding] 当前模型不支持文本接口。请设置 rag.embedding.use-multimodal-endpoint=true 以使用多模态接口仅文本调用。");
            } else {
                log.error("[Embedding] 调用 embedding 接口失败: {}", msg);
            }
            return List.of();
        }
    }

    private List<float[]> embedBatchWithTextEndpoint(RestClient client, String model, List<String> texts) {
        Map<String, Object> body = Map.of(
                "model", model,
                "input", texts,
                "encoding_format", "float"
        );
        Map<?, ?> response = client.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        return parseEmbeddings(response);
    }

    private List<float[]> embedBatchWithMultimodal(RestClient client, String model, List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", List.of(Map.of("type", "text", "text", text != null ? text : "")),
                    "encoding_format", "float"
            );
            Map<?, ?> response = client.post()
                    .uri("/embeddings/multimodal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            List<float[]> singleResult = parseEmbeddings(response);
            if (singleResult.isEmpty()) {
                log.warn("[Embedding] 多模态接口未返回文本向量");
                continue;
            }
            results.add(singleResult.get(0));
        }
        return results;
    }

    private List<float[]> parseEmbeddings(Map<?, ?> response) {
        if (response == null || !response.containsKey("data")) {
            log.error("[Embedding] 响应中无 data 字段");
            return List.of();
        }

        Object dataObj = response.get("data");
        List<?> dataList;
        if (dataObj instanceof List<?> list) {
            dataList = list;
        } else if (dataObj instanceof Map<?, ?> map) {
            dataList = List.of(map);
        } else {
            log.error("[Embedding] 响应 data 格式异常: {}", dataObj == null ? "null" : dataObj.getClass().getName());
            return List.of();
        }

        List<float[]> results = new ArrayList<>();
        for (Object item : dataList) {
            Map<?, ?> entry = (Map<?, ?>) item;
            Object emb = entry.get("embedding");
            if (!(emb instanceof List<?> embeddingValues)) {
                log.warn("[Embedding] 某项无 embedding 字段或非数组");
                continue;
            }
            float[] vec = new float[embeddingValues.size()];
            for (int i = 0; i < embeddingValues.size(); i++) {
                vec[i] = ((Number) embeddingValues.get(i)).floatValue();
            }
            results.add(vec);
        }
        return results;
    }
}
