package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.ingest.parser.DocxDocumentParser;
import com.example.rag.ingest.parser.HtmlDocumentParser;
import com.example.rag.llamaindex.LlamaIndexClient;
import com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexIngestResponse;
import com.example.rag.retrieval.FullTextSearchService;
import com.example.rag.service.dto.UploadOptions;
import com.example.rag.service.dto.UploadResult;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 文档离线入库服务核心类
 * 
 * 功能：
 *   1. 加载多格式文档（PDF/DOCX/HTML/MD/TXT/CSV/URL）
 *   2. 文档分片与元数据 enrichment
 *   3. 构建全文索引（Lucene）
 *   4. 向量入库（Chroma）
 *   5. 可选：LlamaIndex侧车入库
 * 
 * 入库方式：
 *   - 离线入库：ingestAll() 扫描指定目录批量入库
 *   - 上传入库：ingestUploads() API接收上传文件入库
 * 
 * @see IngestController 上传入库API入口
 * @see BatchEmbeddingIngestor 批量向量入库
 * @see FullTextSearchService 全文索引服务
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final RagProperties properties;
    private final EmbeddingStoreIngestor ingestor;
    private final BatchEmbeddingIngestor batchEmbeddingIngestor;
    private final FullTextSearchService fullTextSearchService;
    private final DocumentSplitter splitter;
    private final DocumentMetadataService metadataService;
    private final LlamaIndexClient llamaIndexClient;

    public IngestService(RagProperties properties,
                         EmbeddingStoreIngestor ingestor,
                         BatchEmbeddingIngestor batchEmbeddingIngestor,
                         FullTextSearchService fullTextSearchService,
                         DocumentSplitter splitter,
                         DocumentMetadataService metadataService,
                         LlamaIndexClient llamaIndexClient) {
        this.properties = properties;
        this.ingestor = ingestor;
        this.batchEmbeddingIngestor = batchEmbeddingIngestor;
        this.fullTextSearchService = fullTextSearchService;
        this.splitter = splitter;
        this.metadataService = metadataService;
        this.llamaIndexClient = llamaIndexClient;
    }

    // 执行全量入库
    public void ingestAll() {
        RagProperties.Ingest ingest = properties.getIngest();
        List<Document> documents = new ArrayList<>();
        RagProperties.LlamaIndex.Mode mode = properties.getLlamaindex().getMode();

        if (mode != RagProperties.LlamaIndex.Mode.LANGCHAIN4J && llamaIndexClient != null) {
            ingestAllWithLlamaIndex(ingest);
            if (mode == RagProperties.LlamaIndex.Mode.LLAMAINDEX) {
                return;
            }
        }

        // 记录离线入库起始参数，便于排障
        log.info("离线入库开始, pdfDir={}, mdDir={}, webUrls={}, version={}, tags={}",
                ingest.getPdfDir(),
                ingest.getMdDir(),
                ingest.getWebUrls(),
                ingest.getVersion(),
                ingest.getTags());
        documents.addAll(loadPdf(ingest.getPdfDir(), ingest));
        documents.addAll(loadMarkdown(ingest.getMdDir(), ingest));
        documents.addAll(loadWeb(ingest.getWebUrls(), ingest));

        log.info("离线入库加载完成, totalDocs={}", documents.size());
        if (documents.isEmpty()) {
            log.warn("未发现可入库文档，跳过入库");
            return;
        }

        // 全文索引构建
        if (properties.getFulltext().isEnabled()) {
            log.info("全文索引构建开始, rebuild={}", properties.getFulltext().isRebuildOnIngest());
            if (properties.getFulltext().isRebuildOnIngest()) {
                fullTextSearchService.rebuildIndex(splitter.splitAll(documents));
            } else {
                fullTextSearchService.indexSegments(splitter.splitAll(documents));
            }
            log.info("全文索引构建完成");
        }

        // 向量入库支持批量嵌入
        if (ingest.isBatchEnabled()) {
            log.info("向量入库开始, mode=batch, batchSize={}", ingest.getBatchSize());
            batchEmbeddingIngestor.ingest(documents);
        } else {
            log.info("向量入库开始, mode=single");
            ingestor.ingest(documents);
        }
        log.info("离线入库完成, 文档数={}", documents.size());
    }

    // 上传文件入库：保存文件、构建全文索引与向量入库
    public UploadResult ingestUploads(List<MultipartFile> files, UploadOptions options) {
        if (files == null || files.isEmpty()) {
            return new UploadResult(0, 0, List.of());
        }
        RagProperties.LlamaIndex.Mode mode = properties.getLlamaindex().getMode();
        if (mode == RagProperties.LlamaIndex.Mode.LLAMAINDEX && llamaIndexClient != null) {
            LlamaIndexIngestResponse response = llamaIndexClient.ingestUploads(files, options);
            return toUploadResult(response);
        }
        if (mode == RagProperties.LlamaIndex.Mode.DUAL && llamaIndexClient != null) {
            llamaIndexClient.ingestUploads(files, options);
        }
        RagProperties.Ingest ingest = properties.getIngest();
        String version = resolveVersion(options, ingest);
        List<String> tags = resolveTags(options, ingest);
        String source = resolveSource(options);
        List<String> allowedExtensions = normalizeExtensions(ingest.getAllowedExtensions());
        log.info("上传入库开始, files={}, version={}, tags={}, source={}, allowedExtensions={}",
                files.size(),
                version,
                tags,
                source,
                allowedExtensions);

        Path uploadDir = Path.of(ingest.getUploadDir());
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("创建上传目录失败: " + uploadDir, e);
        }

        List<Document> documents = new ArrayList<>();
        List<String> storedPaths = new ArrayList<>();
        int stored = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalName = file.getOriginalFilename();
            String extension = extractExtension(originalName);
            if (!isAllowedExtension(extension, allowedExtensions)) {
                log.warn("跳过不支持的上传格式: {}", originalName);
                continue;
            }
            // 保存上传文件并构建文档
            String storedName = buildStoredName(originalName, extension);
            Path storedPath = uploadDir.resolve(storedName);
            try {
                Files.copy(file.getInputStream(), storedPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("保存上传文件失败: " + originalName, e);
            }
            DocumentParser parser = resolveParser(extension);
            Document doc = FileSystemDocumentLoader.loadDocument(storedPath, parser);
            Document enriched = enrich(doc, source, storedPath.toString(), version, tags);
            documents.add(enriched);
            storedPaths.add(storedPath.toString());
            stored++;
            metadataService.save(source, storedPath.toString(), version, String.join(",", tags), "INGESTED");
        }

        if (documents.isEmpty()) {
            log.info("上传入库结束, ingested=0, stored={}, skipped={}", stored, files.size() - stored);
            return new UploadResult(0, stored, storedPaths);
        }

        if (properties.getFulltext().isEnabled()) {
            fullTextSearchService.indexSegments(splitter.splitAll(documents));
        }

        if (ingest.isBatchEnabled()) {
            batchEmbeddingIngestor.ingest(documents);
        } else {
            ingestor.ingest(documents);
        }

        log.info("上传入库完成, ingested={}, stored={}, skipped={}",
                documents.size(),
                stored,
                files.size() - stored);
        return new UploadResult(documents.size(), stored, storedPaths);
    }

    private void ingestAllWithLlamaIndex(RagProperties.Ingest ingest) {
        List<Resource> pdfResources = toResources(listFiles(ingest.getPdfDir(), List.of("pdf")));
        if (!pdfResources.isEmpty()) {
            llamaIndexClient.ingestResources(pdfResources,
                    new UploadOptions(ingest.getVersion(), ingest.getTags(), "pdf"));
        }
        List<String> allowed = normalizeExtensions(ingest.getAllowedExtensions());
        allowed = allowed.stream().filter(ext -> !"pdf".equalsIgnoreCase(ext)).toList();
        List<Resource> mdResources = toResources(listFiles(ingest.getMdDir(), allowed));
        if (!mdResources.isEmpty()) {
            llamaIndexClient.ingestResources(mdResources,
                    new UploadOptions(ingest.getVersion(), ingest.getTags(), "markdown"));
        }
        List<String> urls = readUrls(ingest.getWebUrls());
        if (!urls.isEmpty()) {
            llamaIndexClient.ingestUrls(urls,
                    new UploadOptions(ingest.getVersion(), ingest.getTags(), "web"));
        }
    }

    private List<Path> listFiles(String dir, List<String> allowedExtensions) {
        if (dir == null || dir.isBlank()) {
            return List.of();
        }
        Path path = Path.of(dir);
        if (!Files.exists(path)) {
            log.warn("目录不存在: {}", dir);
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(path)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(file -> isAllowedExtension(extractExtension(file.getFileName().toString()), allowedExtensions))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("扫描目录失败: " + dir, e);
        }
    }

    private List<Resource> toResources(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return paths.stream()
                .map(FileSystemResource::new)
                .toList();
    }

    private List<String> readUrls(String urlsFile) {
        if (urlsFile == null || urlsFile.isBlank()) {
            return List.of();
        }
        Path path = Path.of(urlsFile);
        if (!Files.exists(path)) {
            log.warn("URL 列表不存在: {}", urlsFile);
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> urls = new ArrayList<>();
            for (String line : lines) {
                String url = line == null ? "" : line.trim();
                if (url.isEmpty() || url.startsWith("#")) {
                    continue;
                }
                urls.add(url);
            }
            return urls;
        } catch (IOException e) {
            throw new IllegalStateException("读取 URL 列表失败: " + urlsFile, e);
        }
    }

    private UploadResult toUploadResult(LlamaIndexIngestResponse response) {
        if (response == null) {
            return new UploadResult(0, 0, List.of());
        }
        return new UploadResult(response.ingested(), response.stored(), List.of());
    }

    private List<Document> loadPdf(String dir, RagProperties.Ingest ingest) {
        Path path = Path.of(dir);
        if (!Files.exists(path)) {
            log.warn("PDF 目录不存在: {}", dir);
            return List.of();
        }
        List<Document> loaded = FileSystemDocumentLoader.loadDocumentsRecursively(
                path, new ApachePdfBoxDocumentParser());
        return enrichAndRecord(loaded, "pdf", dir, ingest);
    }

    private List<Document> loadMarkdown(String dir, RagProperties.Ingest ingest) {
        Path path = Path.of(dir);
        if (!Files.exists(path)) {
            log.warn("Markdown 目录不存在: {}", dir);
            return List.of();
        }
        List<Document> loaded = FileSystemDocumentLoader.loadDocumentsRecursively(
                path, new TextDocumentParser(StandardCharsets.UTF_8));
        return enrichAndRecord(loaded, "markdown", dir, ingest);
    }

    private List<Document> loadWeb(String urlsFile, RagProperties.Ingest ingest) {
        Path path = Path.of(urlsFile);
        if (!Files.exists(path)) {
            log.warn("URL 列表不存在: {}", urlsFile);
            return List.of();
        }
        List<Document> documents = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String url = line == null ? "" : line.trim();
                if (url.isEmpty() || url.startsWith("#")) {
                    continue;
                }
                Document doc = UrlDocumentLoader.load(url, new TextDocumentParser(StandardCharsets.UTF_8));
                documents.add(enrich(doc, "web", url, ingest));
                metadataService.save("web", url, ingest.getVersion(), String.join(",", ingest.getTags()), "INGESTED");
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 URL 列表失败: " + urlsFile, e);
        }
        return documents;
    }

    private List<Document> enrichAndRecord(List<Document> docs, String source, String path, RagProperties.Ingest ingest) {
        if (docs.isEmpty()) {
            return docs;
        }
        List<Document> enriched = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            enriched.add(enrich(doc, source, path, ingest));
            metadataService.save(source, path, ingest.getVersion(), String.join(",", ingest.getTags()), "INGESTED");
        }
        return enriched;
    }

    private Document enrich(Document doc, String source, String path, RagProperties.Ingest ingest) {
        return enrich(doc, source, path, ingest.getVersion(), ingest.getTags());
    }

    private Document enrich(Document doc, String source, String path, String version, List<String> tags) {
        List<String> safeTags = tags == null ? List.of() : tags;
        Metadata metadata = doc.metadata().copy()
                .put("source", source)
                .put("path", path)
                .put("version", version)
                .put("tags", String.join(",", safeTags));
        return Document.from(doc.text(), metadata);
    }

    private String resolveVersion(UploadOptions options, RagProperties.Ingest ingest) {
        if (options == null || options.version() == null || options.version().isBlank()) {
            return ingest.getVersion();
        }
        return options.version().trim();
    }

    private List<String> resolveTags(UploadOptions options, RagProperties.Ingest ingest) {
        if (options == null || options.tags() == null || options.tags().isEmpty()) {
            return ingest.getTags();
        }
        return options.tags();
    }

    private String resolveSource(UploadOptions options) {
        if (options == null || options.source() == null || options.source().isBlank()) {
            return "upload";
        }
        return options.source().trim();
    }

    private List<String> normalizeExtensions(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            normalized.add(item.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private boolean isAllowedExtension(String extension, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (extension == null || extension.isBlank()) {
            return false;
        }
        return allowed.contains(extension.toLowerCase(Locale.ROOT));
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String buildStoredName(String originalName, String extension) {
        String baseName = (originalName == null || originalName.isBlank())
                ? "upload" + (extension.isBlank() ? "" : "." + extension)
                : Path.of(originalName).getFileName().toString();
        return UUID.randomUUID() + "_" + baseName;
    }

    private DocumentParser resolveParser(String extension) {
        if (isPdfExtension(extension)) {
            return new ApachePdfBoxDocumentParser();
        }
        if (isDocxExtension(extension)) {
            return new DocxDocumentParser();
        }
        if (isHtmlExtension(extension)) {
            return new HtmlDocumentParser();
        }
        // Markdown/TXT/LOG/CSV 等文本类统一走 TextDocumentParser
        return new TextDocumentParser(StandardCharsets.UTF_8);
    }

    private boolean isPdfExtension(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    private boolean isDocxExtension(String extension) {
        return "docx".equalsIgnoreCase(extension);
    }

    private boolean isHtmlExtension(String extension) {
        return "html".equalsIgnoreCase(extension) || "htm".equalsIgnoreCase(extension);
    }
}
