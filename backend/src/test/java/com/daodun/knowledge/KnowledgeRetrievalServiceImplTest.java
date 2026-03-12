package com.daodun.knowledge;

import com.daodun.config.RagProperties;
import com.daodun.entity.KnowledgeChunk;
import com.daodun.repository.KnowledgeChunkRepository;
import com.daodun.service.EmbeddingService;
import com.daodun.service.impl.KnowledgeRetrievalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 知识库检索服务单元测试：验证 retrieve 在 mock 向量/关键词召回下的合并逻辑与返回内容。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalServiceImplTest {

    @Mock
    private KnowledgeChunkRepository chunkRepository;

    @Mock
    private EmbeddingService embeddingService;

    private RagProperties ragProperties;
    private KnowledgeRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getRetrieval().setTopK(5);
        service = new KnowledgeRetrievalServiceImpl(chunkRepository, embeddingService, ragProperties);
    }

    @Test
    @DisplayName("关键词检索能命中并返回对应条目")
    void retrieve_byKeyword_returnsMatchingChunk() {
        String positionName = "Java后端开发";
        String queryText = "JVM JDK JRE 三者的关系";
        List<String> keywords = List.of("JVM");

        KnowledgeChunk chunk = KnowledgeChunk.builder()
                .id(1L)
                .documentId(1L)
                .positionName(positionName)
                .title("JVM、JDK、JRE 三者关系")
                .categoryLevel1("Java基础")
                .categoryLevel2("运行环境与工具链")
                .difficulty(1)
                .answerKeyPoints("JVM 是 Java 虚拟机...")
                .searchText("JVM JDK JRE 字节码")
                .sourceOrder(1)
                .build();

        when(embeddingService.embed(anyString())).thenReturn(new float[0]);
        when(chunkRepository.findByKeyword(eq(positionName), eq("JVM"), anyInt()))
                .thenReturn(List.of(chunk));

        List<KnowledgeChunk> result = service.retrieve(positionName, queryText, keywords);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).contains("JVM");
        assertThat(result.get(0).getTitle()).contains("JDK");
        assertThat(result.get(0).getTitle()).contains("JRE");
    }

    @Test
    @DisplayName("向量召回失败时仅关键词仍可返回结果")
    void retrieve_vectorFails_keywordStillWorks() {
        String positionName = "Java后端开发";
        List<String> keywords = List.of("equals");

        KnowledgeChunk chunk = KnowledgeChunk.builder()
                .id(2L)
                .documentId(2L)
                .positionName(positionName)
                .title("== 和 equals() 有什么区别")
                .searchText("equals hashCode 比较")
                .sourceOrder(19)
                .build();

        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("embedding 服务不可用"));
        when(chunkRepository.findByKeyword(eq(positionName), eq("equals"), anyInt()))
                .thenReturn(List.of(chunk));

        List<KnowledgeChunk> result = service.retrieve(positionName, "equals 和 ==", keywords);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).contains("equals");
    }

    @Test
    @DisplayName("无关键词且向量为空时返回空列表")
    void retrieve_noKeyword_emptyVector_returnsEmpty() {
        when(embeddingService.embed(anyString())).thenReturn(new float[0]);

        List<KnowledgeChunk> result = service.retrieve("Java后端开发", "随便问问", null);

        assertThat(result).isEmpty();
    }
}
