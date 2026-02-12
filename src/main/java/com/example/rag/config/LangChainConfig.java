package com.example.rag.config;

import com.example.rag.perf.QueryLimiter;
import com.example.rag.perf.RagCache;
import com.example.rag.rerank.DjlCrossEncoderScoringModel;
import com.example.rag.retrieval.FullTextSearchService;
import com.example.rag.service.BatchEmbeddingIngestor;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.chroma.ChromaApiVersion;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class LangChainConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(RagProperties properties) {
        RagProperties.Chroma chroma = properties.getChroma();
        return ChromaEmbeddingStore.builder()
                .apiVersion(ChromaApiVersion.V2)
                .baseUrl(chroma.getBaseUrl())
                .tenantName(chroma.getTenant())
                .databaseName(chroma.getDatabase())
                .collectionName(chroma.getCollection())
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public ChatModel chatModel(RagProperties properties) {
        RagProperties.Ollama ollama = properties.getOllama();
        return OllamaChatModel.builder()
                .baseUrl(ollama.getBaseUrl())
                .modelName(ollama.getModelName())
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public DocumentSplitter documentSplitter(RagProperties properties) {
        RagProperties.Ingest ingest = properties.getIngest();
        return DocumentSplitters.recursive(ingest.getChunkSize(), ingest.getChunkOverlap());
    }

    @Bean
    public EmbeddingStoreIngestor embeddingStoreIngestor(DocumentSplitter splitter,
                                                         EmbeddingModel embeddingModel,
                                                         EmbeddingStore<TextSegment> embeddingStore) {
        return EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    @Bean
    public BatchEmbeddingIngestor batchEmbeddingIngestor(DocumentSplitter splitter,
                                                         EmbeddingModel embeddingModel,
                                                         EmbeddingStore<TextSegment> embeddingStore,
                                                         RagProperties properties) {
        return new BatchEmbeddingIngestor(splitter, embeddingModel, embeddingStore,
                properties.getIngest().getBatchSize());
    }

    @Bean
    public FullTextSearchService fullTextSearchService(RagProperties properties) {
        return new FullTextSearchService(properties);
    }

    @Bean
    public RagCache ragCache(RagProperties properties) {
        return new RagCache(properties);
    }

    @Bean
    public QueryLimiter queryLimiter(RagProperties properties) {
        RagProperties.Concurrency concurrency = properties.getConcurrency();
        if (!concurrency.isEnabled()) {
            return QueryLimiter.disabled();
        }
        return QueryLimiter.threadPool(concurrency.getName(),
                concurrency.getCoreSize(),
                concurrency.getMaxSize(),
                concurrency.getQueueCapacity());
    }

    @Bean
    @ConditionalOnProperty(prefix = "rag.retrieval.rerank.cross-encoder", name = "enabled", havingValue = "true")
    public ScoringModel scoringModel(RagProperties properties) {
        RagProperties.Retrieval.CrossEncoder crossEncoder = properties.getRetrieval().getRerank().getCrossEncoder();
        if (crossEncoder.getModelPath() != null && !crossEncoder.getModelPath().isBlank()) {
            return new DjlCrossEncoderScoringModel(Path.of(crossEncoder.getModelPath()),
                    crossEncoder.isIncludeTokenTypes(),
                    crossEncoder.isSigmoid());
        }
        return new DjlCrossEncoderScoringModel(crossEncoder.getModelId(),
                crossEncoder.isIncludeTokenTypes(),
                crossEncoder.isSigmoid());
    }
}
