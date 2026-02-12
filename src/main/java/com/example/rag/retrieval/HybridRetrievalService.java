package com.example.rag.retrieval;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// 混合检索融合器：基于RRF融合多路候选
public class HybridRetrievalService {

    private final ContentRetriever vectorRetriever;
    private final ContentRetriever fullTextRetriever;

    public HybridRetrievalService(ContentRetriever vectorRetriever, ContentRetriever fullTextRetriever) {
        this.vectorRetriever = Objects.requireNonNull(vectorRetriever, "vectorRetriever不能为空");
        this.fullTextRetriever = Objects.requireNonNull(fullTextRetriever, "fullTextRetriever不能为空");
    }

    // 从向量与全文检索中融合结果
    public List<Content> retrieve(Query query, int topK) {
        List<Content> vector = safeRetrieve(vectorRetriever, query);
        List<Content> fullText = safeRetrieve(fullTextRetriever, query);
        return fuse(vector, fullText, topK);
    }

    // 执行RRF融合并裁剪TopK
    public List<Content> fuse(List<Content> vector, List<Content> fullText, int topK) {
        List<List<Content>> inputs = new ArrayList<>();
        if (vector != null && !vector.isEmpty()) {
            inputs.add(vector);
        }
        if (fullText != null && !fullText.isEmpty()) {
            inputs.add(fullText);
        }
        if (inputs.isEmpty()) {
            return List.of();
        }
        return ReciprocalRankFuser.fuse(inputs, topK);
    }

    private List<Content> safeRetrieve(ContentRetriever retriever, Query query) {
        List<Content> result = retriever.retrieve(query);
        return result == null ? List.of() : result;
    }
}
