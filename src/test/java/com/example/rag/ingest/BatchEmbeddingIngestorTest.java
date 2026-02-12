package com.example.rag.ingest;

import com.example.rag.service.BatchEmbeddingIngestor;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchEmbeddingIngestorTest {

    @Test
    void ingest_shouldBatchEmbed() {
        AtomicInteger calls = new AtomicInteger();
        EmbeddingModel model = segments -> {
            calls.incrementAndGet();
            return Response.from(segments.stream()
                    .map(segment -> dev.langchain4j.data.embedding.Embedding.from(new float[]{1.0f}))
                    .toList());
        };
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        BatchEmbeddingIngestor ingestor = new BatchEmbeddingIngestor(doc -> List.of(
                TextSegment.from(doc.text(), doc.metadata())
        ), model, store, 2);

        List<Document> docs = List.of(
                Document.from("A"),
                Document.from("B"),
                Document.from("C")
        );
        ingestor.ingest(docs);

        assertEquals(2, calls.get());
    }
}
