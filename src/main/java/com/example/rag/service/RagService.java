package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.llamaindex.LlamaIndexClient;
import com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexQueryItem;
import com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexQueryResponse;
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
import com.example.rag.service.dto.UploadOptions;
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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 问答检索服务核心类
 * 
 * 功能：
 *   1. 接收用户问答请求
 *   2. 执行混合检索（向量检索 + 全文检索 + RRF融合）
 *   3. 可选重排序（关键词重排 / CrossEncoder重排）
 *   4. 调用LLM生成答案
 *   5. 结果缓存与并发限流保护
 * 
 * 检索模式：
 *   - langchain4j：仅使用LangChain4j向量检索
 *   - llamaindex：仅使用LlamaIndex侧车检索
 *   - dual：双路并行检索，结果合并
 * 
 * @see RagController 问答API入口
 * @see RagRequest 请求参数
 * @see RagResponse 返回结果
 */
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
    private final LlamaIndexClient llamaIndexClient;
    private final KeywordReranker keywordReranker = new KeywordReranker();

    public RagService(EmbeddingModel embeddingModel,
                      EmbeddingStore<TextSegment> embeddingStore,
                      ChatModel chatModel,
                      RagProperties properties,
                      FullTextSearchService fullTextSearchService,
                      RagCache ragCache,
                      QueryLimiter queryLimiter,
                      @Nullable ScoringModel scoringModel,
                      @Nullable LlamaIndexClient llamaIndexClient) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatModel = chatModel;
        this.properties = properties;
        this.fullTextSearchService = fullTextSearchService;
        this.ragCache = ragCache;
        this.queryLimiter = queryLimiter;
        this.scoringModel = scoringModel;
        this.llamaIndexClient = llamaIndexClient;
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
        RagProperties.LlamaIndex.Mode mode = properties.getLlamaindex().getMode();

        // 参数日志：核心检索配置
        log.info("开始检索, topK={}, minScore={}, candidateSize={}, hybrid={}, crossEncoder={}, keywordRerank={}",
                topK,
                minScore,
                candidateSize,
                properties.getRetrieval().getHybrid().isEnabled(),
                crossEncoder.isEnabled(),
                rerank.isKeywordEnabled());

        LlamaIndexOutcome llamaIndexOutcome = retrieveWithLlamaIndex(request, topK, minScore, mode);
        if (mode == RagProperties.LlamaIndex.Mode.LLAMAINDEX) {
            if (llamaIndexOutcome.evidence().isEmpty()) {
                log.info("LlamaIndex 无候选内容，直接返回 NO_MATCH");
                return new RagResponse(NO_MATCH, List.of(), List.of());
            }
            return buildAnswer(request, llamaIndexOutcome.evidence(), llamaIndexOutcome.sources());
        }

        RetrievalOutcome langchainOutcome = retrieveWithLangChain4j(
                request,
                topK,
                minScore,
                candidateSize,
                filter,
                fullTextFilter,
                rerank,
                crossEncoder
        );

        List<String> evidence = langchainOutcome.evidence();
        List<String> sources = langchainOutcome.sources();

        if (mode == RagProperties.LlamaIndex.Mode.DUAL && !llamaIndexOutcome.evidence().isEmpty()) {
            evidence = mergeEvidence(llamaIndexOutcome.evidence(), evidence, topK);
            sources = mergeSources(llamaIndexOutcome.sources(), sources);
        }

        if (evidence.isEmpty()) {
            log.info("证据为空，直接返回 NO_MATCH");
            return new RagResponse(NO_MATCH, List.of(), List.of());
        }

        return buildAnswer(request, evidence, sources);
    }

    private RetrievalOutcome retrieveWithLangChain4j(RagRequest request,
                                                     int topK,
                                                     double minScore,
                                                     int candidateSize,
                                                     Filter filter,
                                                     FullTextFilter fullTextFilter,
                                                     RagProperties.Retrieval.Rerank rerank,
                                                     RagProperties.Retrieval.CrossEncoder crossEncoder) {
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
            return new RetrievalOutcome(List.of(), List.of());
        }

        List<TextSegment> segments = fused.stream()
                .map(Content::textSegment)
                .toList();

        List<TextSegment> rankedSegments = segments;
        List<String> evidence;

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
            return new RetrievalOutcome(List.of(), List.of());
        }

        List<String> sources = rankedSegments.stream()
                .map(TextSegment::metadata)
                .map(this::toSource)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        return new RetrievalOutcome(evidence, sources);
    }

    private LlamaIndexOutcome retrieveWithLlamaIndex(RagRequest request,
                                                     int topK,
                                                     double minScore,
                                                     RagProperties.LlamaIndex.Mode mode) {
        if (mode == RagProperties.LlamaIndex.Mode.LANGCHAIN4J || llamaIndexClient == null) {
            return new LlamaIndexOutcome(List.of(), List.of());
        }
        UploadOptions options = new UploadOptions(request.version(), request.tags(), request.source());
        LlamaIndexQueryResponse response = llamaIndexClient.query(request.question(), topK, options);
        if (response == null || response.items() == null || response.items().isEmpty()) {
            return new LlamaIndexOutcome(List.of(), List.of());
        }
        List<LlamaIndexQueryItem> filtered = response.items().stream()
                .filter(item -> item != null && item.score() >= minScore)
                .limit(topK)
                .toList();
        List<String> evidence = filtered.stream()
                .map(LlamaIndexQueryItem::text)
                .filter(text -> text != null && !text.isBlank())
                .toList();
        List<String> sources = filtered.stream()
                .map(LlamaIndexQueryItem::metadata)
                .map(this::toSource)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        return new LlamaIndexOutcome(evidence, sources);
    }

    private RagResponse buildAnswer(RagRequest request, List<String> evidence, List<String> sources) {
        String prompt = buildPrompt(request.question(), evidence);
        String answer = chatModel.chat(prompt);
        log.info("生成完成, answerLen={}, sources={}", answer == null ? 0 : answer.length(), sources.size());
        return new RagResponse(answer, evidence, sources);
    }

    private List<String> mergeEvidence(List<String> primary, List<String> secondary, int limit) {
        Set<String> merged = new LinkedHashSet<>();
        if (primary != null) {
            merged.addAll(primary);
        }
        if (secondary != null) {
            merged.addAll(secondary);
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        return merged.stream().limit(limit).toList();
    }

    private List<String> mergeSources(List<String> primary, List<String> secondary) {
        Set<String> merged = new LinkedHashSet<>();
        if (primary != null) {
            merged.addAll(primary);
        }
        if (secondary != null) {
            merged.addAll(secondary);
        }
        return merged.stream().toList();
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

    private String toSource(java.util.Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        String source = metadata.get("source");
        String path = metadata.get("path");
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

    private record RetrievalOutcome(List<String> evidence, List<String> sources) {
    }

    private record LlamaIndexOutcome(List<String> evidence, List<String> sources) {
    }
}
