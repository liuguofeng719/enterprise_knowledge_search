package com.example.rag.service.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RagRequest(
        @NotBlank String question,
        Integer topK,
        Double minScore,
        String version,
        List<String> tags,
        String source,
        List<String> keywords
) {
}
