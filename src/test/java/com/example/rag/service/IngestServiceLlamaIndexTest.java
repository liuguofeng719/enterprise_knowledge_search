package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.llamaindex.LlamaIndexClient;
import com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexIngestResponse;
import com.example.rag.service.dto.UploadOptions;
import com.example.rag.service.dto.UploadResult;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import com.example.rag.retrieval.FullTextSearchService;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestServiceLlamaIndexTest {

    @Test
    void ingestUploads_shouldUseLlamaIndexWhenModeEnabled() {
        RagProperties properties = new RagProperties();
        properties.getLlamaindex().setMode(RagProperties.LlamaIndex.Mode.LLAMAINDEX);

        EmbeddingStoreIngestor ingestor = Mockito.mock(EmbeddingStoreIngestor.class);
        BatchEmbeddingIngestor batchEmbeddingIngestor = Mockito.mock(BatchEmbeddingIngestor.class);
        FullTextSearchService fullTextSearchService = Mockito.mock(FullTextSearchService.class);
        DocumentSplitter splitter = Mockito.mock(DocumentSplitter.class);
        DocumentMetadataService metadataService = Mockito.mock(DocumentMetadataService.class);
        LlamaIndexClient llamaIndexClient = Mockito.mock(LlamaIndexClient.class);

        Mockito.when(llamaIndexClient.ingestUploads(Mockito.anyList(), Mockito.any()))
                .thenReturn(new LlamaIndexIngestResponse(1, 1, List.of()));

        IngestService ingestService = new IngestService(
                properties,
                ingestor,
                batchEmbeddingIngestor,
                fullTextSearchService,
                splitter,
                metadataService,
                llamaIndexClient
        );

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "a.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        UploadResult result = ingestService.ingestUploads(
                List.of(file),
                new UploadOptions("v1", List.of("guide"), "upload")
        );

        assertThat(result.ingested()).isEqualTo(1);
        assertThat(result.stored()).isEqualTo(1);
        Mockito.verify(llamaIndexClient).ingestUploads(Mockito.anyList(), Mockito.any());
        Mockito.verifyNoInteractions(ingestor, batchEmbeddingIngestor, fullTextSearchService, splitter, metadataService);
    }
}
