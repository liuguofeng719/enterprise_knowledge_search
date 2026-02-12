package com.example.rag.retrieval;

import java.util.List;

public record FullTextFilter(String source, String version, List<String> tags) {
}
