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

        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = Map.of(
                "model", ragProperties.getEmbedding().getModel(),
                "input", texts,
                "encoding_format", "float"
        );

        long start = System.currentTimeMillis();
        Map<?, ?> response;
        try {
            response = client.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("[Embedding] 调用 embedding 接口失败: {}", e.getMessage());
            return List.of();
        }
        log.info("[Embedding] 调用 embedding 接口成功, {} 条文本, 耗时 {}ms",
                texts.size(), System.currentTimeMillis() - start);

        if (response == null || !response.containsKey("data")) {
            log.error("[Embedding] 响应中无 data 字段");
            return List.of();
        }

        List<?> dataList = (List<?>) response.get("data");
        List<float[]> results = new ArrayList<>();
        for (Object item : dataList) {
            Map<?, ?> entry = (Map<?, ?>) item;
            List<?> embeddingValues = (List<?>) entry.get("embedding");
            float[] vec = new float[embeddingValues.size()];
            for (int i = 0; i < embeddingValues.size(); i++) {
                vec[i] = ((Number) embeddingValues.get(i)).floatValue();
            }
            results.add(vec);
        }
        return results;
    }
}
