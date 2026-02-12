package com.example.rag.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private final Chroma chroma = new Chroma();
    private final Ollama ollama = new Ollama();
    private final Retrieval retrieval = new Retrieval();
    private final FullText fulltext = new FullText();
    private final Cache cache = new Cache();
    private final Concurrency concurrency = new Concurrency();
    private final Ingest ingest = new Ingest();

    public Chroma getChroma() {
        return chroma;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public FullText getFulltext() {
        return fulltext;
    }

    public Cache getCache() {
        return cache;
    }

    public Concurrency getConcurrency() {
        return concurrency;
    }

    public Ingest getIngest() {
        return ingest;
    }

    public static class Chroma {
        @NotBlank
        private String baseUrl = "http://localhost:8000";
        @NotBlank
        private String collection = "kb";
        private String tenant = "default_tenant";
        private String database = "default_database";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }
    }

    public static class Ollama {
        @NotBlank
        private String baseUrl = "http://localhost:11434";
        @NotBlank
        private String modelName = "llama3.1";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }

    public static class Retrieval {
        @Min(1)
        private int topK = 5;
        @Min(0)
        private double minScore = 0.2;
        @Min(1)
        private int candidateSize = 20;
        private final Hybrid hybrid = new Hybrid();
        private final Rerank rerank = new Rerank();

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public int getCandidateSize() {
            return candidateSize;
        }

        public void setCandidateSize(int candidateSize) {
            this.candidateSize = candidateSize;
        }

        public Hybrid getHybrid() {
            return hybrid;
        }

        public Rerank getRerank() {
            return rerank;
        }

        public static class Hybrid {
            private boolean enabled = true;
            @Min(1)
            private int fullTextTopK = 20;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getFullTextTopK() {
                return fullTextTopK;
            }

            public void setFullTextTopK(int fullTextTopK) {
                this.fullTextTopK = fullTextTopK;
            }
        }

        public static class Rerank {
            private boolean keywordEnabled = true;
            @Min(0)
            private double keywordBoost = 0.1;
            private final CrossEncoder crossEncoder = new CrossEncoder();

            public boolean isKeywordEnabled() {
                return keywordEnabled;
            }

            public void setKeywordEnabled(boolean keywordEnabled) {
                this.keywordEnabled = keywordEnabled;
            }

            public double getKeywordBoost() {
                return keywordBoost;
            }

            public void setKeywordBoost(double keywordBoost) {
                this.keywordBoost = keywordBoost;
            }

            public CrossEncoder getCrossEncoder() {
                return crossEncoder;
            }
        }

        public static class CrossEncoder {
            private boolean enabled = false;
            @Min(1)
            private int topK = 10;
            private String modelId = "cross-encoder/ms-marco-MiniLM-L-6-v2";
            private String modelPath;
            private boolean includeTokenTypes = true;
            private boolean sigmoid = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getTopK() {
                return topK;
            }

            public void setTopK(int topK) {
                this.topK = topK;
            }

            public String getModelId() {
                return modelId;
            }

            public void setModelId(String modelId) {
                this.modelId = modelId;
            }

            public String getModelPath() {
                return modelPath;
            }

            public void setModelPath(String modelPath) {
                this.modelPath = modelPath;
            }

            public boolean isIncludeTokenTypes() {
                return includeTokenTypes;
            }

            public void setIncludeTokenTypes(boolean includeTokenTypes) {
                this.includeTokenTypes = includeTokenTypes;
            }

            public boolean isSigmoid() {
                return sigmoid;
            }

            public void setSigmoid(boolean sigmoid) {
                this.sigmoid = sigmoid;
            }
        }
    }

    public static class FullText {
        private boolean enabled = true;
        @NotBlank
        private String indexPath = "data/fulltext";
        private boolean rebuildOnIngest = true;
        @Min(0)
        private int cacheMaxEntries = 1024;
        @Min(0)
        private int cacheMaxRamMb = 64;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIndexPath() {
            return indexPath;
        }

        public void setIndexPath(String indexPath) {
            this.indexPath = indexPath;
        }

        public boolean isRebuildOnIngest() {
            return rebuildOnIngest;
        }

        public void setRebuildOnIngest(boolean rebuildOnIngest) {
            this.rebuildOnIngest = rebuildOnIngest;
        }

        public int getCacheMaxEntries() {
            return cacheMaxEntries;
        }

        public void setCacheMaxEntries(int cacheMaxEntries) {
            this.cacheMaxEntries = cacheMaxEntries;
        }

        public int getCacheMaxRamMb() {
            return cacheMaxRamMb;
        }

        public void setCacheMaxRamMb(int cacheMaxRamMb) {
            this.cacheMaxRamMb = cacheMaxRamMb;
        }
    }

    public static class Cache {
        private boolean enabled = true;
        private final Spec embedding = new Spec();
        private final Spec result = new Spec();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Spec getEmbedding() {
            return embedding;
        }

        public Spec getResult() {
            return result;
        }

        public static class Spec {
            @Min(1)
            private int maxSize = 1000;
            private Duration ttl = Duration.ofMinutes(30);

            public int getMaxSize() {
                return maxSize;
            }

            public void setMaxSize(int maxSize) {
                this.maxSize = maxSize;
            }

            public Duration getTtl() {
                return ttl;
            }

            public void setTtl(Duration ttl) {
                this.ttl = ttl;
            }
        }
    }

    public static class Concurrency {
        private boolean enabled = true;
        @Min(1)
        private int coreSize = 4;
        @Min(1)
        private int maxSize = 8;
        @Min(0)
        private int queueCapacity = 64;
        private String name = "rag-ask";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Ingest {
        @NotBlank
        private String pdfDir = "samples/pdf";
        @NotBlank
        private String mdDir = "samples/md";
        @NotBlank
        private String webUrls = "samples/urls.txt";
        @NotBlank
        private String uploadDir = "data/uploads";
        private List<String> allowedExtensions = List.of("pdf", "md", "markdown", "txt", "log", "csv", "docx", "html", "htm");
        @NotBlank
        private String version = "v1";
        private List<String> tags = List.of("guide");
        @Min(100)
        private int chunkSize = 800;
        @Min(0)
        private int chunkOverlap = 120;
        private boolean batchEnabled = true;
        @Min(1)
        private int batchSize = 32;

        public String getPdfDir() {
            return pdfDir;
        }

        public void setPdfDir(String pdfDir) {
            this.pdfDir = pdfDir;
        }

        public String getMdDir() {
            return mdDir;
        }

        public void setMdDir(String mdDir) {
            this.mdDir = mdDir;
        }

        public String getWebUrls() {
            return webUrls;
        }

        public void setWebUrls(String webUrls) {
            this.webUrls = webUrls;
        }

        public String getUploadDir() {
            return uploadDir;
        }

        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }

        public boolean isBatchEnabled() {
            return batchEnabled;
        }

        public void setBatchEnabled(boolean batchEnabled) {
            this.batchEnabled = batchEnabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
