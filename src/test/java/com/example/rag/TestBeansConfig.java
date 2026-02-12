package com.example.rag;

import com.example.rag.config.RagProperties;
import com.example.rag.perf.QueryLimiter;
import com.example.rag.perf.RagCache;
import com.example.rag.retrieval.FullTextSearchService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

@TestConfiguration
public class TestBeansConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        return segments -> Response.from(segments.stream()
                .map(segment -> Embedding.from(new float[]{segment.text().length(), 1.0f, 0.5f}))
                .toList());
    }

    @Bean
    @Primary
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    @Primary
    public ChatModel chatModel() {
        return new ChatModel() {
            @Override
            public String chat(String message) {
                return "测试回答";
            }

            @Override
            public ChatResponse chat(dev.langchain4j.model.chat.request.ChatRequest request) {
                return ChatResponse.builder()
                        .aiMessage(dev.langchain4j.data.message.AiMessage.from("测试回答"))
                        .build();
            }
        };
    }

    @Bean
    @Primary
    public FullTextSearchService fullTextSearchService(RagProperties properties) {
        properties.getFulltext().setEnabled(false);
        return new FullTextSearchService(properties);
    }

    @Bean
    @Primary
    public RagCache ragCache(RagProperties properties) {
        properties.getCache().setEnabled(true);
        return new RagCache(properties);
    }

    @Bean
    @Primary
    public QueryLimiter queryLimiter() {
        return QueryLimiter.disabled();
    }
}
