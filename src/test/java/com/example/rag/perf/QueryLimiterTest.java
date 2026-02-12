package com.example.rag.perf;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryLimiterTest {

    @Test
    void execute_shouldRejectWhenQueueFull() throws Exception {
        QueryLimiter limiter = QueryLimiter.threadPool("test", 1, 1, 0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> limiter.execute(() -> {
            entered.countDown();
            await(release);
            return "ok";
        }));

        assertTrue(entered.await(2, TimeUnit.SECONDS));

        assertThrows(BulkheadFullException.class, () -> limiter.execute(() -> "second"));

        release.countDown();
        first.get(2, TimeUnit.SECONDS);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
