package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.llamaindex.LlamaIndexClient;
import com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexQueryItem;
import com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexQueryResponse;
import com.example.rag.perf.QueryLimiter;
import com.example.rag.perf.RagCache;
import com.example.rag.retrieval.FullTextSearchService;
import com.example.rag.service.dto.RagRequest;
import com.example.rag.service.dto.RagResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagServiceLlamaIndexTest {

    @Test
    void ask_shouldUseLlamaIndexWhenModeEnabled() {
        RagProperties properties = new RagProperties();
        properties.getLlamaindex().setMode(RagProperties.LlamaIndex.Mode.LLAMAINDEX);

        EmbeddingModel embeddingModel = segments -> Response.from(List.of());
        EmbeddingStore<TextSegment> embeddingStore = new EmbeddingStore<>() {
            @Override
            public String add(dev.langchain4j.data.embedding.Embedding embedding) {
                return "id";
            }

            @Override
            public void add(String id, dev.langchain4j.data.embedding.Embedding embedding) {
            }

            @Override
            public String add(dev.langchain4j.data.embedding.Embedding embedding, TextSegment embedded) {
                return "id";
            }

            @Override
            public List<String> addAll(List<dev.langchain4j.data.embedding.Embedding> embeddings) {
                return List.of();
            }

            @Override
            public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
                return new EmbeddingSearchResult<>(List.of());
            }
        };
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("答案"))
                        .build();
            }
        };

        LlamaIndexClient llamaIndexClient = Mockito.mock(LlamaIndexClient.class);
        Mockito.when(llamaIndexClient.query(Mockito.eq("问题"), Mockito.any(), Mockito.any()))
                .thenReturn(new LlamaIndexQueryResponse(List.of(
                        new LlamaIndexQueryItem("llama-evidence", 0.9,
                                Map.of("source", "upload", "path", "/a")))));

        RagCache cache = new RagCache(properties);
        QueryLimiter limiter = QueryLimiter.disabled();
        FullTextSearchService fullTextSearchService = new FullTextSearchService(properties);

        RagService ragService = new RagService(embeddingModel, embeddingStore, chatModel, properties,
                fullTextSearchService, cache, limiter, null, llamaIndexClient);

        RagResponse response = ragService.ask(new RagRequest("问题", null, null, null, null, null, null));

        assertThat(response.evidence()).contains("llama-evidence");
        assertThat(response.sources()).contains("upload:/a");
        Mockito.verify(llamaIndexClient).query(Mockito.eq("问题"), Mockito.any(), Mockito.any());
    }
}
