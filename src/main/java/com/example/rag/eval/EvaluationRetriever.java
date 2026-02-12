package com.example.rag.eval;

import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

@FunctionalInterface
public interface EvaluationRetriever {
    List<TextSegment> retrieve(String question, int topK);
}
