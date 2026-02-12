package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.perf.QueryLimiter;
import com.example.rag.perf.RagCache;
import com.example.rag.retrieval.FullTextSearchService;
import com.example.rag.service.dto.RagRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagServiceCacheIntegrationTest {

    @Test
    void ask_shouldUseResultCache() {
        RagProperties properties = new RagProperties();
        properties.getCache().setEnabled(true);
        properties.getRetrieval().getHybrid().setEnabled(false);
        properties.getRetrieval().getRerank().setKeywordEnabled(false);
        properties.getFulltext().setEnabled(false);

        AtomicInteger embedCalls = new AtomicInteger();
        EmbeddingModel embeddingModel = segments -> {
            embedCalls.incrementAndGet();
            return Response.from(segments.stream()
                    .map(segment -> Embedding.from(new float[]{1.0f}))
                    .toList());
        };

        AtomicInteger chatCalls = new AtomicInteger();
        ChatModel chatModel = new ChatModel() {
            @Override
            public String chat(String message) {
                chatCalls.incrementAndGet();
                return "答案";
            }

            @Override
            public ChatResponse chat(dev.langchain4j.model.chat.request.ChatRequest request) {
                chatCalls.incrementAndGet();
                return ChatResponse.builder()
                        .aiMessage(dev.langchain4j.data.message.AiMessage.from("答案"))
                        .build();
            }
        };

        TextSegment segment = TextSegment.from("证据");
        EmbeddingStore<TextSegment> embeddingStore = new EmbeddingStore<>() {
            @Override
            public String add(Embedding embedding) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(String id, Embedding embedding) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String add(Embedding embedding, TextSegment embedded) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<String> addAll(List<Embedding> embeddings) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
                return new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.9, "id", null, segment)
                ));
            }
        };

        RagCache cache = new RagCache(properties);
        QueryLimiter limiter = QueryLimiter.disabled();
        FullTextSearchService fullTextSearchService = new FullTextSearchService(properties);

        RagService ragService = new RagService(embeddingModel, embeddingStore, chatModel, properties,
                fullTextSearchService, cache, limiter, null);

        RagRequest request = new RagRequest("问题", null, null, null, null, null, null);

        ragService.ask(request);
        ragService.ask(request);

        assertEquals(1, embedCalls.get());
        assertEquals(1, chatCalls.get());
    }
}
