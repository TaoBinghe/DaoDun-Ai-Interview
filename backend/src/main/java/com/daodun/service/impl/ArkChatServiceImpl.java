package com.daodun.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.daodun.service.ArkChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class ArkChatServiceImpl implements ArkChatService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

        logLlmInput("chat/completions", messages);

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

    @Override
    public String chatWithMessagesStream(List<Map<String, String>> messages, Consumer<String> onDelta) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("请配置环境变量 ARK_API_KEY");
        }

        HttpClient client = HttpClient.newHttpClient();
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "stream", true
        );

        logLlmInput("chat/completions(stream)", messages);

        long start = System.currentTimeMillis();
        StringBuilder fullText = new StringBuilder();
        try {
            String body = MAPPER.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("方舟流式接口报错: HTTP " + response.statusCode() + " " + err);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                        continue;
                    }
                    String data = trimmed.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    String delta = extractStreamDelta(data);
                    if (delta == null || delta.isEmpty()) {
                        continue;
                    }
                    fullText.append(delta);
                    onDelta.accept(delta);
                }
            }
        } catch (Exception e) {
            log.error("[ArkChat] 流式调用方舟接口失败，耗时 {}ms: {}", System.currentTimeMillis() - start, e.getMessage());
            throw new IllegalStateException("调用方舟流式接口失败: " + e.getMessage(), e);
        }
        log.info("[ArkChat] 流式调用方舟接口成功，耗时 {}ms", System.currentTimeMillis() - start);
        return fullText.toString();
    }

    /** 输出发往模型的完整 messages，便于核对提示词是否生效 */
    private void logLlmInput(String api, List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            log.info("[ArkChat][模型输入] {} messages=空", api);
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> m = messages.get(i);
            String role = m != null ? m.get("role") : null;
            String content = m != null ? m.get("content") : null;
            if (content != null && content.length() > 2000) {
                log.info("[ArkChat][模型输入] {} message[{}] role={} content长度={} 前2000字:\n{}", api, i, role, content.length(), content.substring(0, 2000));
            } else {
                log.info("[ArkChat][模型输入] {} message[{}] role={}\ncontent:\n{}", api, i, role, content);
            }
        }
    }

    private String extractStreamDelta(String data) {
        try {
            JsonNode root = MAPPER.readTree(data);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                return "";
            }
            JsonNode first = choices.get(0);
            JsonNode delta = first.path("delta");
            if (delta.has("content")) {
                return delta.get("content").asText("");
            }
            JsonNode message = first.path("message");
            if (message.has("content")) {
                return message.get("content").asText("");
            }
            return "";
        } catch (Exception e) {
            log.debug("[ArkChat] 解析流式分片失败: {}", e.getMessage());
            return "";
        }
    }
}
