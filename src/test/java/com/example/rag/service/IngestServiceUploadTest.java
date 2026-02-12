package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.retrieval.FullTextSearchService;
import com.example.rag.service.dto.UploadOptions;
import com.example.rag.service.dto.UploadResult;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestServiceUploadTest {

    @Test
    void upload_txt_shouldBeIngested(@TempDir Path tempDir) {
        RagProperties properties = new RagProperties();
        properties.getIngest().setUploadDir(tempDir.toString());
        properties.getIngest().setBatchEnabled(false);
        properties.getFulltext().setEnabled(false);

        EmbeddingStoreIngestor ingestor = Mockito.mock(EmbeddingStoreIngestor.class);
        BatchEmbeddingIngestor batchEmbeddingIngestor = Mockito.mock(BatchEmbeddingIngestor.class);
        FullTextSearchService fullTextSearchService = Mockito.mock(FullTextSearchService.class);
        DocumentSplitter splitter = Mockito.mock(DocumentSplitter.class);
        DocumentMetadataService metadataService = Mockito.mock(DocumentMetadataService.class);

        IngestService ingestService = new IngestService(
                properties,
                ingestor,
                batchEmbeddingIngestor,
                fullTextSearchService,
                splitter,
                metadataService
        );

        MockMultipartFile file = new MockMultipartFile(
                "files", "a.txt", "text/plain", "hello".getBytes()
        );

        UploadResult result = ingestService.ingestUploads(List.of(file), new UploadOptions(null, List.of(), null));

        assertEquals(1, result.ingested());
        assertEquals(1, result.stored());
        assertEquals(1, result.storedPaths().size());
        assertTrue(result.storedPaths().get(0).endsWith(".txt"));
        Mockito.verify(ingestor).ingest(Mockito.anyList());
    }

    @Test
    void upload_docx_and_html_shouldBeParsed(@TempDir Path tempDir) throws Exception {
        RagProperties properties = new RagProperties();
        properties.getIngest().setUploadDir(tempDir.toString());
        properties.getIngest().setBatchEnabled(false);
        properties.getFulltext().setEnabled(false);

        EmbeddingStoreIngestor ingestor = Mockito.mock(EmbeddingStoreIngestor.class);
        BatchEmbeddingIngestor batchEmbeddingIngestor = Mockito.mock(BatchEmbeddingIngestor.class);
        FullTextSearchService fullTextSearchService = Mockito.mock(FullTextSearchService.class);
        DocumentSplitter splitter = Mockito.mock(DocumentSplitter.class);
        DocumentMetadataService metadataService = Mockito.mock(DocumentMetadataService.class);

        IngestService ingestService = new IngestService(
                properties,
                ingestor,
                batchEmbeddingIngestor,
                fullTextSearchService,
                splitter,
                metadataService
        );

        byte[] docxBytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("docx 文本");
            document.write(out);
            docxBytes = out.toByteArray();
        }

        MockMultipartFile docx = new MockMultipartFile(
                "files",
                "demo.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );
        MockMultipartFile html = new MockMultipartFile(
                "files",
                "demo.html",
                "text/html",
                "<html><body>hello html</body></html>".getBytes(StandardCharsets.UTF_8)
        );

        UploadResult result = ingestService.ingestUploads(List.of(docx, html), new UploadOptions(null, List.of(), null));

        assertEquals(2, result.ingested());
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(ingestor).ingest(captor.capture());
        List<Document> docs = captor.getValue();
        assertEquals(2, docs.size());

        List<String> texts = docs.stream().map(Document::text).toList();
        assertTrue(texts.stream().anyMatch(text -> text.contains("docx 文本")));
        assertTrue(texts.stream().anyMatch(text -> text.contains("hello html")));
        assertTrue(texts.stream().noneMatch(text -> text.contains("<html")));
    }

    @Test
    void upload_shouldLogSummary(@TempDir Path tempDir) {
        RagProperties properties = new RagProperties();
        properties.getIngest().setUploadDir(tempDir.toString());
        properties.getIngest().setBatchEnabled(false);
        properties.getFulltext().setEnabled(false);

        EmbeddingStoreIngestor ingestor = Mockito.mock(EmbeddingStoreIngestor.class);
        BatchEmbeddingIngestor batchEmbeddingIngestor = Mockito.mock(BatchEmbeddingIngestor.class);
        FullTextSearchService fullTextSearchService = Mockito.mock(FullTextSearchService.class);
        DocumentSplitter splitter = Mockito.mock(DocumentSplitter.class);
        DocumentMetadataService metadataService = Mockito.mock(DocumentMetadataService.class);

        IngestService ingestService = new IngestService(
                properties,
                ingestor,
                batchEmbeddingIngestor,
                fullTextSearchService,
                splitter,
                metadataService
        );

        Logger logger = (Logger) LoggerFactory.getLogger(IngestService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        MockMultipartFile file = new MockMultipartFile(
                "files", "a.txt", "text/plain", "hello".getBytes()
        );

        ingestService.ingestUploads(List.of(file), new UploadOptions(null, List.of(), null));

        List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertTrue(messages.stream().anyMatch(msg -> msg.contains("上传入库完成")));

        logger.detachAppender(appender);
    }
}
