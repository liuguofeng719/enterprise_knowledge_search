package com.example.rag.eval;

import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

// 离线评测服务：计算命中率与Recall@K
@Service
public class EvaluationService {

    // 执行评测
    public EvaluationReport evaluate(EvaluationDataset dataset, EvaluationRetriever retriever, int topK) {
        if (dataset == null || dataset.cases() == null || dataset.cases().isEmpty()) {
            return new EvaluationReport(0, 0, 0.0, 0.0);
        }
        int total = dataset.cases().size();
        int hits = 0;
        for (EvaluationCase one : dataset.cases()) {
            List<TextSegment> segments = retriever.retrieve(one.question(), topK);
            if (hit(one, segments)) {
                hits++;
            }
        }
        double hitRate = total == 0 ? 0.0 : (double) hits / total;
        double recallAtK = hitRate;
        return new EvaluationReport(total, hits, hitRate, recallAtK);
    }

    private boolean hit(EvaluationCase one, List<TextSegment> segments) {
        if (one == null || one.expectedKeywords() == null || one.expectedKeywords().isEmpty()) {
            return false;
        }
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        List<String> expected = one.expectedKeywords().stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> k.toLowerCase(Locale.ROOT))
                .toList();
        if (expected.isEmpty()) {
            return false;
        }
        for (TextSegment segment : segments) {
            String text = segment == null ? "" : segment.text();
            String lower = text.toLowerCase(Locale.ROOT);
            for (String key : expected) {
                if (lower.contains(key)) {
                    return true;
                }
            }
        }
        return false;
    }
}
