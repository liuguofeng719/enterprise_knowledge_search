package com.example.rag.perf;

import com.example.rag.config.RagProperties;
import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagCacheTest {

    @Test
    void getEmbedding_shouldReuseCachedValue() {
        RagProperties properties = new RagProperties();
        properties.getCache().setEnabled(true);
        properties.getCache().getEmbedding().setMaxSize(10);
        properties.getCache().getEmbedding().setTtl(Duration.ofMinutes(10));

        RagCache cache = new RagCache(properties);
        AtomicInteger calls = new AtomicInteger();

        Embedding first = cache.getEmbedding("q", () -> {
            calls.incrementAndGet();
            return Embedding.from(new float[]{1.0f});
        });
        Embedding second = cache.getEmbedding("q", () -> {
            calls.incrementAndGet();
            return Embedding.from(new float[]{2.0f});
        });

        assertEquals(1, calls.get());
        assertEquals(first, second);
    }
}
