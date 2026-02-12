package com.example.rag.eval;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationServiceTest {

    @Test
    void evaluate_shouldComputeHitRate() {
        EvaluationDataset dataset = new EvaluationDataset(List.of(
                new EvaluationCase("Q1", List.of("A")),
                new EvaluationCase("Q2", List.of("B"))
        ));

        EvaluationService service = new EvaluationService();
        EvaluationReport report = service.evaluate(dataset, (question, topK) -> {
            if ("Q1".equals(question)) {
                return List.of(TextSegment.from("包含A 的段落"));
            }
            return List.of(TextSegment.from("无关内容"));
        }, 3);

        assertEquals(2, report.total());
        assertEquals(1, report.hitCount());
        assertEquals(0.5, report.hitRate(), 0.0001);
        assertEquals(0.5, report.recallAtK(), 0.0001);
    }
}
