package com.example.rag.retrieval;

import com.example.rag.config.RagProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullTextSearchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void search_shouldApplyMetadataFilters() {
        RagProperties properties = new RagProperties();
        properties.getFulltext().setEnabled(true);
        properties.getFulltext().setIndexPath(tempDir.toString());

        FullTextSearchService service = new FullTextSearchService(properties);

        TextSegment a = TextSegment.from("产品A 使用指南", new Metadata()
                .put("source", "pdf")
                .put("path", "a.pdf")
                .put("version", "v1")
                .put("tags", "guide,api"));
        TextSegment b = TextSegment.from("产品B API 说明", new Metadata()
                .put("source", "markdown")
                .put("path", "b.md")
                .put("version", "v2")
                .put("tags", "api"));
        TextSegment c = TextSegment.from("运维手册", new Metadata()
                .put("source", "pdf")
                .put("path", "c.pdf")
                .put("version", "v1")
                .put("tags", "ops"));

        service.rebuildIndex(List.of(a, b, c));

        FullTextFilter filter = new FullTextFilter("pdf", "v1", List.of("guide"));
        List<TextSegment> results = service.search("指南", filter, 5);

        assertEquals(1, results.size());
        assertTrue(results.get(0).text().contains("产品A"));
    }
}
