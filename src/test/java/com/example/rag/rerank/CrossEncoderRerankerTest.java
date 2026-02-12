package com.example.rag.rerank;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrossEncoderRerankerTest {

    @Test
    void rerank_shouldSortByScore() {
        ScoringModel scoringModel = (segments, query) -> Response.from(List.of(0.2, 0.9, 0.1));
        CrossEncoderReranker reranker = new CrossEncoderReranker(scoringModel);

        List<TextSegment> segments = List.of(
                TextSegment.from("A"),
                TextSegment.from("B"),
                TextSegment.from("C")
        );

        List<CrossEncoderReranker.ScoredSegment> ranked = reranker.rerank("问题", segments);

        assertEquals("B", ranked.get(0).segment().text());
        assertEquals("A", ranked.get(1).segment().text());
        assertEquals("C", ranked.get(2).segment().text());
    }
}
