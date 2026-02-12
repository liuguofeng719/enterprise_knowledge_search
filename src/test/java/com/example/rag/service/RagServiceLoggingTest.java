package com.example.rag.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
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
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RagServiceLoggingTest {

    @Test
    void ask_shouldLogCoreSteps() {
        RagProperties properties = new RagProperties();
        properties.getCache().setEnabled(true);
        properties.getRetrieval().getHybrid().setEnabled(false);
        properties.getRetrieval().getRerank().setKeywordEnabled(false);
        properties.getFulltext().setEnabled(false);

        EmbeddingModel embeddingModel = segments -> Response.from(segments.stream()
                .map(segment -> Embedding.from(new float[]{1.0f}))
                .toList());

        ChatModel chatModel = new ChatModel() {
            @Override
            public String chat(String message) {
                return "答案";
            }

            @Override
            public ChatResponse chat(dev.langchain4j.model.chat.request.ChatRequest request) {
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

        Logger logger = (Logger) LoggerFactory.getLogger(RagService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        ragService.ask(new RagRequest("问题", null, null, null, null, null, null));

        List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertTrue(messages.stream().anyMatch(msg -> msg.contains("问答请求开始")));
        assertTrue(messages.stream().anyMatch(msg -> msg.contains("生成完成")));

        logger.detachAppender(appender);
    }
}
