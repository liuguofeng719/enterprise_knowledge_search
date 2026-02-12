package com.example.rag.perf;

import com.example.rag.config.RagProperties;
import com.example.rag.service.dto.RagResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.embedding.Embedding;

import java.time.Duration;
import java.util.function.Supplier;

// 查询嵌入与问答结果缓存
public class RagCache {

    private final RagProperties.Cache properties;
    private final Cache<String, Embedding> embeddingCache;
    private final Cache<String, RagResponse> resultCache;

    public RagCache(RagProperties properties) {
        this.properties = properties.getCache();
        this.embeddingCache = buildEmbeddingCache();
        this.resultCache = buildResultCache();
    }

    // 获取或加载查询向量
    public Embedding getEmbedding(String key, Supplier<Embedding> loader) {
        if (!properties.isEnabled()) {
            return loader.get();
        }
        return embeddingCache.get(key, k -> loader.get());
    }

    // 读取结果缓存
    public RagResponse getResult(String key) {
        if (!properties.isEnabled()) {
            return null;
        }
        return resultCache.getIfPresent(key);
    }

    // 读取或计算结果缓存
    public RagResponse getOrComputeResult(String key, Supplier<RagResponse> loader) {
        if (!properties.isEnabled()) {
            return loader.get();
        }
        return resultCache.get(key, k -> loader.get());
    }

    // 写入结果缓存
    public void putResult(String key, RagResponse response) {
        if (!properties.isEnabled()) {
            return;
        }
        resultCache.put(key, response);
    }

    private Cache<String, Embedding> buildEmbeddingCache() {
        RagProperties.Cache.Spec spec = properties.getEmbedding();
        return buildCache(spec.getMaxSize(), spec.getTtl());
    }

    private Cache<String, RagResponse> buildResultCache() {
        RagProperties.Cache.Spec spec = properties.getResult();
        return buildCache(spec.getMaxSize(), spec.getTtl());
    }

    private <T> Cache<String, T> buildCache(int maxSize, Duration ttl) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .build();
    }
}
