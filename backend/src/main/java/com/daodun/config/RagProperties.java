package com.daodun.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private boolean enabled = true;

    private Embedding embedding = new Embedding();
    private Retrieval retrieval = new Retrieval();
    private Ingestion ingestion = new Ingestion();
    private Prompt prompt = new Prompt();

    @Data
    public static class Embedding {
        private String model = "doubao-embedding-large-text-240915";
        private int dimensions = 1024;
    }

    @Data
    public static class Retrieval {
        private int topK = 3;
        private double minScore = 0.3;
    }

    @Data
    public static class Ingestion {
        private String sourceDir = "QAdocs";
    }

    @Data
    public static class Prompt {
        private int maxChars = 2000;
    }
}
