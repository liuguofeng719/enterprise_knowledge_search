package com.example.rag.perf;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;

import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

// 并发限流/排队封装，基于Resilience4j线程池隔离
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
