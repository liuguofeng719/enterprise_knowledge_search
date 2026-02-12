package com.example.rag.eval;

public record EvaluationReport(int total, int hitCount, double hitRate, double recallAtK) {
}
