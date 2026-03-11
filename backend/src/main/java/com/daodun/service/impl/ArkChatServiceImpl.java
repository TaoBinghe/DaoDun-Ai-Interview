package com.daodun.service.impl;

import com.daodun.service.ArkChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ArkChatServiceImpl implements ArkChatService {

    @Value("${spring.ai.openai.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:doubao-seed-1-6-flash-250828}")
    private String model;

    @Override
    public String chat(String userMessage) {
        return chatWithMessages(List.of(Map.of("role", "user", "content", userMessage)));
    }

    @Override
    public String chatWithMessages(List<Map<String, String>> messages) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("请配置环境变量 ARK_API_KEY");
        }

        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages
        );

        long start = System.currentTimeMillis();
        Map<?, ?> response;
        try {
            response = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("[ArkChat] 调用方舟接口失败，耗时 {}ms: {}", System.currentTimeMillis() - start, e.getMessage());
            throw new IllegalStateException("调用方舟接口失败: " + e.getMessage(), e);
        }
        log.info("[ArkChat] 调用方舟接口成功，耗时 {}ms", System.currentTimeMillis() - start);

        if (response == null) {
            throw new IllegalStateException("方舟返回为空");
        }
        if (response.containsKey("error")) {
            Map<?, ?> err = (Map<?, ?>) response.get("error");
            String msg = err != null && err.get("message") != null
                    ? err.get("message").toString() : response.toString();
            throw new IllegalStateException("方舟接口报错: " + msg);
        }

        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("方舟返回中无 choices");
        }
        Map<?, ?> first = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) first.get("message");
        if (message == null) {
            throw new IllegalStateException("方舟返回中无 message");
        }
        Object content = message.get("content");
        return content != null ? content.toString() : "";
    }
}
