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
        /** 火山方舟：推理接入点 ID (ep-xxxxx)。文本向量化接入点或（仅文本时）多模态接入点均可。 */
        private String model = "";
        private int dimensions = 1024;
        /** 为 true 时使用 /embeddings/multimodal 接口，input 仅传 type=text（适用于仅有多模态接入点的情况）。 */
        private boolean useMultimodalEndpoint = false;

        public boolean getUseMultimodalEndpoint() {
            return useMultimodalEndpoint;
        }
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
