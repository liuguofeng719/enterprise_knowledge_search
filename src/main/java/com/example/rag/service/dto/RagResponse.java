package com.example.rag.service.dto;

import java.util.List;

public record RagResponse(
        String answer,
        List<String> evidence,
        List<String> sources
) {
}
