package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.perf.QueryLimiter;
import com.example.rag.perf.RagCache;
import com.example.rag.rerank.CrossEncoderReranker;
import com.example.rag.rerank.KeywordReranker;
import com.example.rag.retrieval.FullTextContentRetriever;
import com.example.rag.retrieval.FullTextFilter;
import com.example.rag.retrieval.FullTextSearchService;
import com.example.rag.retrieval.HybridRetrievalService;
import com.example.rag.retrieval.VectorContentRetriever;
import com.example.rag.service.dto.RagRequest;
import com.example.rag.service.dto.RagResponse;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

// 检索与生成的主入口，负责混合检索、重排、缓存与限流
@Service
public class RagService {

    private static final String NO_MATCH = "未检索到相关内容，请调整问题或过滤条件。";
    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel chatModel;
    private final RagProperties properties;
    private final FullTextSearchService fullTextSearchService;
    private final RagCache ragCache;
    private final QueryLimiter queryLimiter;
    private final ScoringModel scoringModel;
    private final KeywordReranker keywordReranker = new KeywordReranker();

    public RagService(EmbeddingModel embeddingModel,
                      EmbeddingStore<TextSegment> embeddingStore,
                      ChatModel chatModel,
                      RagProperties properties,
                      FullTextSearchService fullTextSearchService,
                      RagCache ragCache,
                      QueryLimiter queryLimiter,
                      @Nullable ScoringModel scoringModel) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatModel = chatModel;
        this.properties = properties;
        this.fullTextSearchService = fullTextSearchService;
        this.ragCache = ragCache;
        this.queryLimiter = queryLimiter;
        this.scoringModel = scoringModel;
    }

    // 对外问答入口：先查结果缓存，再进入限流执行
    public RagResponse ask(RagRequest request) {
        // 入口日志：只记录关键统计信息，避免日志污染
        log.info("问答请求开始, questionLen={}, version={}, source={}, tags={}, keywords={}",
                request.question() == null ? 0 : request.question().length(),
                normalize(request.version()),
                normalize(request.source()),
                normalizeList(request.tags()),
                normalizeList(request.keywords()));
        String cacheKey = buildCacheKey(request);
        RagResponse cached = ragCache.getResult(cacheKey);
        if (cached != null) {
            log.info("命中结果缓存");
            return cached;
        }
        return queryLimiter.execute(() -> {
            // 二次检查缓存，减少并发抖动
            RagResponse secondCheck = ragCache.getResult(cacheKey);
            if (secondCheck != null) {
                log.info("命中结果缓存（二次检查）");
                return secondCheck;
            }
            RagResponse response = doAsk(request);
            ragCache.putResult(cacheKey, response);
            log.info("问答请求完成, answerLen={}, evidenceSize={}, sourceSize={}",
                    response.answer() == null ? 0 : response.answer().length(),
                    response.evidence() == null ? 0 : response.evidence().size(),
                    response.sources() == null ? 0 : response.sources().size());
            return response;
        });
    }

    // 实际检索与生成逻辑，包含混合检索与重排
    private RagResponse doAsk(RagRequest request) {
        int topK = request.topK() == null ? properties.getRetrieval().getTopK() : request.topK();
        double minScore = request.minScore() == null ? properties.getRetrieval().getMinScore() : request.minScore();
        int candidateSize = Math.max(topK, properties.getRetrieval().getCandidateSize());
        Filter filter = buildFilter(request);
        FullTextFilter fullTextFilter = buildFullTextFilter(request);
        RagProperties.Retrieval.Rerank rerank = properties.getRetrieval().getRerank();
        RagProperties.Retrieval.CrossEncoder crossEncoder = rerank.getCrossEncoder();

        // 参数日志：核心检索配置
        log.info("开始检索, topK={}, minScore={}, candidateSize={}, hybrid={}, crossEncoder={}, keywordRerank={}",
                topK,
                minScore,
                candidateSize,
                properties.getRetrieval().getHybrid().isEnabled(),
                crossEncoder.isEnabled(),
                rerank.isKeywordEnabled());

        // 向量检索候选
        VectorContentRetriever vectorRetriever = new VectorContentRetriever(
                embeddingModel,
                embeddingStore,
                ragCache,
                filter,
                candidateSize,
                minScore
        );
        List<Content> vectorContents = vectorRetriever.retrieve(Query.from(request.question()));
        log.info("向量候选数: {}", vectorContents == null ? 0 : vectorContents.size());

        List<Content> fused;
        // 混合检索：向量 + 全文
        if (properties.getRetrieval().getHybrid().isEnabled()) {
            int fullTextTopK = properties.getRetrieval().getHybrid().getFullTextTopK();
            FullTextContentRetriever fullTextRetriever = new FullTextContentRetriever(
                    fullTextSearchService,
                    fullTextFilter,
                    fullTextTopK
            );
            HybridRetrievalService hybridService = new HybridRetrievalService(vectorRetriever, fullTextRetriever);
            fused = hybridService.retrieve(Query.from(request.question()), candidateSize);
        } else {
            fused = vectorContents;
        }

        log.info("融合候选数: {}", fused == null ? 0 : fused.size());
        if (fused == null || fused.isEmpty()) {
            log.info("无候选内容，直接返回 NO_MATCH");
            return new RagResponse(NO_MATCH, List.of(), List.of());
        }

        List<TextSegment> segments = fused.stream()
                .map(Content::textSegment)
                .toList();

        List<TextSegment> rankedSegments = segments;
        List<String> evidence;

        // 交叉重排：优先于关键词重排
        if (crossEncoder.isEnabled() && scoringModel != null) {
            CrossEncoderReranker reranker = new CrossEncoderReranker(scoringModel);
            int rerankTopK = Math.min(crossEncoder.getTopK(), segments.size());
            List<TextSegment> topCandidates = segments.subList(0, rerankTopK);
            List<CrossEncoderReranker.ScoredSegment> reranked = reranker.rerank(request.question(), topCandidates);
            List<TextSegment> merged = new ArrayList<>(segments.size());
            for (CrossEncoderReranker.ScoredSegment scored : reranked) {
                merged.add(scored.segment());
            }
            if (segments.size() > rerankTopK) {
                merged.addAll(segments.subList(rerankTopK, segments.size()));
            }
            rankedSegments = merged;
            evidence = rankedSegments.stream()
                    .map(TextSegment::text)
                    .limit(topK)
                    .toList();
            log.info("交叉重排完成, rerankTopK={}, evidenceSize={}", rerankTopK, evidence.size());
        } else if (rerank.isKeywordEnabled() && request.keywords() != null && !request.keywords().isEmpty()) {
            // 关键词重排：对候选文本加权
            List<KeywordReranker.CandidateSegment> candidates = new ArrayList<>(segments.size());
            for (int i = 0; i < segments.size(); i++) {
                double baseScore = 1.0 / (i + 1);
                candidates.add(new KeywordReranker.CandidateSegment(segments.get(i).text(), baseScore));
            }
            List<KeywordReranker.RerankedSegment> reranked = keywordReranker
                    .rerank(candidates, request.keywords(), rerank.getKeywordBoost());
            evidence = reranked.stream()
                    .map(KeywordReranker.RerankedSegment::text)
                    .limit(topK)
                    .toList();
            rankedSegments = pickSegmentsByText(segments, evidence);
            log.info("关键词重排完成, evidenceSize={}", evidence.size());
        } else {
            evidence = segments.stream()
                    .map(TextSegment::text)
                    .limit(topK)
                    .toList();
            log.info("未启用重排, evidenceSize={}", evidence.size());
        }

        if (evidence.isEmpty()) {
            log.info("证据为空，直接返回 NO_MATCH");
            return new RagResponse(NO_MATCH, List.of(), List.of());
        }

        List<String> sources = rankedSegments.stream()
                .map(TextSegment::metadata)
                .map(this::toSource)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        String prompt = buildPrompt(request.question(), evidence);
        String answer = chatModel.chat(prompt);
        log.info("生成完成, answerLen={}, sources={}", answer == null ? 0 : answer.length(), sources.size());

        return new RagResponse(answer, evidence, sources);
    }

    private Filter buildFilter(RagRequest request) {
        Filter filter = null;
        if (request.version() != null && !request.version().isBlank()) {
            filter = MetadataFilterBuilder.metadataKey("version").isEqualTo(request.version());
        }
        if (request.source() != null && !request.source().isBlank()) {
            Filter source = MetadataFilterBuilder.metadataKey("source").isEqualTo(request.source());
            filter = merge(filter, source);
        }
        if (request.tags() != null && !request.tags().isEmpty()) {
            Filter tagsFilter = null;
            for (String tag : request.tags()) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                Filter one = MetadataFilterBuilder.metadataKey("tags").containsString(tag);
                tagsFilter = tagsFilter == null ? one : Filter.or(tagsFilter, one);
            }
            filter = merge(filter, tagsFilter);
        }
        return filter;
    }

    private Filter merge(Filter base, Filter next) {
        if (base == null) {
            return next;
        }
        if (next == null) {
            return base;
        }
        return Filter.and(base, next);
    }

    private FullTextFilter buildFullTextFilter(RagRequest request) {
        return new FullTextFilter(request.source(), request.version(), request.tags());
    }

    private String toSource(Metadata metadata) {
        if (metadata == null) {
            return null;
        }
        String source = metadata.getString("source");
        String path = metadata.getString("path");
        if (source == null && path == null) {
            return null;
        }
        if (path == null) {
            return source;
        }
        return source + ":" + path;
    }

    private String buildPrompt(String question, List<String> evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是企业知识库助手，请仅基于给定资料回答问题。");
        sb.append("如果资料不足，请回答：").append(NO_MATCH).append("\\n\\n");
        sb.append("问题：").append(question).append("\n\n");
        sb.append("资料：\n");
        for (int i = 0; i < evidence.size(); i++) {
            sb.append(i + 1).append(". ").append(evidence.get(i)).append("\n");
        }
        return sb.toString();
    }

    // 缓存Key：问题+过滤条件+关键字+TopK/MinScore
    private String buildCacheKey(RagRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(normalize(request.question())).append("|");
        sb.append(normalize(request.version())).append("|");
        sb.append(normalize(request.source())).append("|");
        sb.append(normalizeList(request.tags())).append("|");
        sb.append(normalizeList(request.keywords())).append("|");
        sb.append(request.topK() == null ? "" : request.topK()).append("|");
        sb.append(request.minScore() == null ? "" : request.minScore());
        return sb.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining(","));
    }

    // 将重排后的文本映射回原始分片
    private List<TextSegment> pickSegmentsByText(List<TextSegment> segments, List<String> texts) {
        if (segments == null || segments.isEmpty() || texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<TextSegment> selected = new ArrayList<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            for (TextSegment segment : segments) {
                if (segment.text().equals(text)) {
                    selected.add(segment);
                    break;
                }
            }
        }
        return selected.isEmpty() ? segments : selected;
    }
}
