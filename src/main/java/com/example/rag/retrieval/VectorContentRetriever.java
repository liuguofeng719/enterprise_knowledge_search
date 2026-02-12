package com.example.rag.retrieval;

import com.example.rag.perf.RagCache;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.List;

// 向量检索适配为ContentRetriever，支持缓存与过滤
public class VectorContentRetriever implements ContentRetriever {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RagCache ragCache;
    private final Filter filter;
    private final int topK;
    private final double minScore;

    public VectorContentRetriever(EmbeddingModel embeddingModel,
                                  EmbeddingStore<TextSegment> embeddingStore,
                                  RagCache ragCache,
                                  Filter filter,
                                  int topK,
                                  double minScore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.ragCache = ragCache;
        this.filter = filter;
        this.topK = topK;
        this.minScore = minScore;
    }

    @Override
    // 执行向量检索
    public List<Content> retrieve(Query query) {
        Embedding embedding = ragCache == null
                ? embeddingModel.embed(query.text()).content()
                : ragCache.getEmbedding(query.text(), () -> embeddingModel.embed(query.text()).content());
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(topK)
                .minScore(minScore)
                .filter(filter)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }
        return matches.stream()
                .map(EmbeddingMatch::embedded)
                .map(Content::from)
                .toList();
    }
}
