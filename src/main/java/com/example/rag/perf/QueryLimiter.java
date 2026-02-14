package com.example.rag.perf;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;

import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * 并发限流服务
 * 
 * 功能：限制同时执行的问答请求数量，防止突发流量压垮系统
 * 
 * 实现：Resilience4j ThreadPoolBulkhead（线程池隔离）
 * - coreSize：核心线程数
 * - maxSize：最大线程数
 * - queueCapacity：队列容量，超出后拒绝请求
 * 
 * 适用场景：
 * - LLM响应慢，需要控制并发
 * - 防止Ollama/Chroma被压垮
 * - 保护下游依赖服务
 * 
 * @see RagProperties.Concurrency 配置项
 */
public class QueryLimiter {

    private final ThreadPoolBulkhead bulkhead;
    private final boolean enabled;

    private QueryLimiter(ThreadPoolBulkhead bulkhead, boolean enabled) {
        this.bulkhead = bulkhead;
        this.enabled = enabled;
    }

    public static QueryLimiter threadPool(String name, int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolBulkheadConfig config = ThreadPoolBulkheadConfig.custom()
                .coreThreadPoolSize(coreSize)
                .maxThreadPoolSize(maxSize)
                .queueCapacity(queueCapacity)
                .build();
        return new QueryLimiter(ThreadPoolBulkhead.of(name, config), true);
    }

    public static QueryLimiter disabled() {
        return new QueryLimiter(null, false);
    }

    // 执行受限流保护的任务
    public <T> T execute(Supplier<T> supplier) {
        if (!enabled) {
            return supplier.get();
        }
        try {
            return bulkhead.executeSupplier(supplier)
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BulkheadFullException bulkheadFull) {
                throw bulkheadFull;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }
}
