package com.example.rag.retrieval;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.List;

// 全文检索适配为ContentRetriever
public class FullTextContentRetriever implements ContentRetriever {

    private final FullTextSearchService fullTextSearchService;
    private final FullTextFilter filter;
    private final int topK;

    public FullTextContentRetriever(FullTextSearchService fullTextSearchService,
                                    FullTextFilter filter,
                                    int topK) {
        this.fullTextSearchService = fullTextSearchService;
        this.filter = filter;
        this.topK = topK;
    }

    @Override
    // 执行全文检索
    public List<Content> retrieve(Query query) {
        List<TextSegment> segments = fullTextSearchService.search(query.text(), filter, topK);
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        return segments.stream()
                .map(Content::from)
                .toList();
    }
}
