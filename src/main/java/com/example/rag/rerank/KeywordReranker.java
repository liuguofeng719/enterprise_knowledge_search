package com.example.rag.rerank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class KeywordReranker {

    public record CandidateSegment(String text, double baseScore) {
    }

    public record RerankedSegment(String text, double score, int keywordHits) {
    }

    public List<RerankedSegment> rerank(List<CandidateSegment> segments, List<String> keywords, double boost) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        if (keywords == null || keywords.isEmpty()) {
            List<RerankedSegment> passthrough = new ArrayList<>(segments.size());
            for (CandidateSegment segment : segments) {
                passthrough.add(new RerankedSegment(segment.text(), segment.baseScore(), 0));
            }
            return passthrough;
        }

        List<String> normalized = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> k.toLowerCase(Locale.ROOT))
                .toList();

        List<RerankedSegment> reranked = new ArrayList<>(segments.size());
        for (CandidateSegment segment : segments) {
            String text = segment.text() == null ? "" : segment.text();
            String lower = text.toLowerCase(Locale.ROOT);
            int hits = 0;
            for (String key : normalized) {
                if (lower.contains(key)) {
                    hits++;
                }
            }
            double score = segment.baseScore() + hits * (1.0 + boost);
            reranked.add(new RerankedSegment(text, score, hits));
        }

        reranked.sort(Comparator.comparingDouble(RerankedSegment::score).reversed());
        return reranked;
    }
}
