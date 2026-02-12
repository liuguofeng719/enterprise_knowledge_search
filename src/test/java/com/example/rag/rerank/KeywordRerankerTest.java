package com.example.rag.rerank;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeywordRerankerTest {

    @Test
    void rerank_shouldBoostSegmentsWithKeywords() {
        List<KeywordReranker.CandidateSegment> input = List.of(
                new KeywordReranker.CandidateSegment("Spring Boot 快速入门", 0.60),
                new KeywordReranker.CandidateSegment("纯向量检索策略", 0.90),
                new KeywordReranker.CandidateSegment("Boot 与 MyBatis-Plus 集成", 0.50)
        );

        List<KeywordReranker.RerankedSegment> output = new KeywordReranker()
                .rerank(input, List.of("boot"), 0.10);

        assertEquals("Spring Boot 快速入门", output.get(0).text());
        assertEquals("Boot 与 MyBatis-Plus 集成", output.get(1).text());
        assertEquals("纯向量检索策略", output.get(2).text());
    }

    @Test
    void rerank_shouldKeepOrderWhenNoKeywords() {
        List<KeywordReranker.CandidateSegment> input = List.of(
                new KeywordReranker.CandidateSegment("A", 0.30),
                new KeywordReranker.CandidateSegment("B", 0.20)
        );

        List<KeywordReranker.RerankedSegment> output = new KeywordReranker()
                .rerank(input, List.of(), 0.10);

        assertEquals("A", output.get(0).text());
        assertEquals("B", output.get(1).text());
    }
}
