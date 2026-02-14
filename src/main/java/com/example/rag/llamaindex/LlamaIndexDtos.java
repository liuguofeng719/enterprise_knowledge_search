package com.example.rag.llamaindex;

import java.util.List;
import java.util.Map;

public final class LlamaIndexDtos {

    private LlamaIndexDtos() {
    }

    public record LlamaIndexQueryRequest(
            String question,
            Integer topK,
            LlamaIndexFilters filters
    ) {
    }

    public record LlamaIndexFilters(
            String version,
            List<String> tags,
            String source
    ) {
    }

    public record LlamaIndexQueryItem(
            String text,
            double score,
            Map<String, String> metadata
    ) {
    }

    public record LlamaIndexQueryResponse(
            List<LlamaIndexQueryItem> items
    ) {
    }

    public record LlamaIndexIngestResponse(
            int ingested,
            int stored,
            List<String> failed
    ) {
    }

    public record LlamaIndexIngestUrlsRequest(
            List<String> urls,
            String version,
            List<String> tags,
            String source
    ) {
    }
}
