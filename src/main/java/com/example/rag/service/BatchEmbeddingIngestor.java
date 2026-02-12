package com.example.rag.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;

// 批量嵌入入库器，降低Embedding调用开销
public class BatchEmbeddingIngestor {

    private final DocumentSplitter splitter;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int batchSize;

    public BatchEmbeddingIngestor(DocumentSplitter splitter,
                                 EmbeddingModel embeddingModel,
                                 EmbeddingStore<TextSegment> embeddingStore,
                                 int batchSize) {
        this.splitter = splitter;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.batchSize = Math.max(1, batchSize);
    }

    // 按批次嵌入并写入向量库
    public void ingest(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<TextSegment> segments = splitter.splitAll(documents);
        if (segments.isEmpty()) {
            return;
        }
        for (int i = 0; i < segments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, segments.size());
            List<TextSegment> batch = segments.subList(i, end);
            Response<List<Embedding>> response = embeddingModel.embedAll(batch);
            List<Embedding> embeddings = response == null ? List.of() : response.content();
            if (embeddings == null || embeddings.size() != batch.size()) {
                throw new IllegalStateException("批量嵌入数量与分片不一致");
            }
            embeddingStore.addAll(embeddings, batch);
        }
    }
}
