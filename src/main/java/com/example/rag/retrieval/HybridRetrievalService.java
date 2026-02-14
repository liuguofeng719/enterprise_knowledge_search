package com.example.rag.retrieval;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 混合检索融合服务
 * 
 * 原理：结合向量检索（语义理解）和全文检索（关键词匹配）的优势
 * - 向量检索：基于Embedding相似度，擅长同义词、概念关联
 * - 全文检索：基于Lucene精确匹配，擅长专有名词、技术术语
 * - RRF融合：Reciprocal Rank Fusion算法融合两路排序结果
 * 
 * 融合公式：score = 1 / (k + rank)，k通常取60
 * 
 * @see VectorContentRetriever 向量检索
 * @see FullTextContentRetriever 全文检索
 */
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
