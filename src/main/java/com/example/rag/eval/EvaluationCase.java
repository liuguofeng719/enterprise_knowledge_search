package com.example.rag.eval;

import java.util.List;

public record EvaluationCase(String question, List<String> expectedKeywords) {
}
