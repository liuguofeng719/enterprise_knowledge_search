package com.example.rag.rerank;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

// 交叉重排：调用ScoringModel对候选段落打分
public class CrossEncoderReranker {

    public record ScoredSegment(TextSegment segment, double score) {
    }

    private final ScoringModel scoringModel;

    public CrossEncoderReranker(ScoringModel scoringModel) {
        this.scoringModel = Objects.requireNonNull(scoringModel, "scoringModel不能为空");
    }

    // 对候选段落进行重排
    public List<ScoredSegment> rerank(String query, List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        Response<List<Double>> response = scoringModel.scoreAll(segments, query);
        List<Double> scores = response == null ? List.of() : response.content();
        if (scores == null || scores.size() != segments.size()) {
            throw new IllegalStateException("交叉重排评分结果与段落数量不一致");
        }
        List<ScoredSegment> ranked = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            ranked.add(new ScoredSegment(segments.get(i), scores.get(i)));
        }
        ranked.sort(Comparator.comparingDouble(ScoredSegment::score).reversed());
        return ranked;
    }
}
