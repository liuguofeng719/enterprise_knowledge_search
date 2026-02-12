package com.example.rag.retrieval;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRetrievalServiceTest {

    @Test
    void retrieve_shouldFuseVectorAndFullTextResults() {
        ContentRetriever vectorRetriever = query -> List.of(
                Content.from(TextSegment.from("A")),
                Content.from(TextSegment.from("B"))
        );
        ContentRetriever fullTextRetriever = query -> List.of(
                Content.from(TextSegment.from("B")),
                Content.from(TextSegment.from("C"))
        );

        HybridRetrievalService service = new HybridRetrievalService(vectorRetriever, fullTextRetriever);

        List<Content> result = service.retrieve(Query.from("test"), 3);

        assertEquals(3, result.size());
        assertEquals("B", result.get(0).textSegment().text());
        assertTrue(result.stream().anyMatch(c -> c.textSegment().text().equals("A")));
        assertTrue(result.stream().anyMatch(c -> c.textSegment().text().equals("C")));
    }
}
