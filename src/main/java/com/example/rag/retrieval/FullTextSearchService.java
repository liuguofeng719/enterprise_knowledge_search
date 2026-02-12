package com.example.rag.retrieval;

import com.example.rag.config.RagProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.QueryCache;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.queryparser.classic.QueryParser;

import jakarta.annotation.PreDestroy;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Lucene全文检索服务，支持元数据过滤与查询缓存
public class FullTextSearchService implements Closeable {

    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_TAGS = "tags";

    private final RagProperties.FullText properties;
    private final Analyzer analyzer;
    private final Directory directory;

    public FullTextSearchService(RagProperties properties) {
        this.properties = properties.getFulltext();
        this.analyzer = new StandardAnalyzer();
        try {
            this.directory = FSDirectory.open(Path.of(this.properties.getIndexPath()));
        } catch (IOException e) {
            throw new IllegalStateException("初始化全文索引目录失败: " + this.properties.getIndexPath(), e);
        }
    }

    // 重建全文索引
    public void rebuildIndex(List<TextSegment> segments) {
        indexSegments(segments, true);
    }

    // 增量写入全文索引
    public void indexSegments(List<TextSegment> segments) {
        indexSegments(segments, false);
    }

    // 执行全文检索并返回分片
    public List<TextSegment> search(String queryText, FullTextFilter filter, int topK) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }
        Query query = buildQuery(queryText, filter);
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryCache cache = buildQueryCache();
            if (cache != null) {
                searcher.setQueryCache(cache);
            }
            TopDocs docs = searcher.search(query, topK);
            List<TextSegment> results = new ArrayList<>(docs.scoreDocs.length);
            for (var scoreDoc : docs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                results.add(toSegment(doc));
            }
            return results;
        } catch (IndexNotFoundException e) {
            return List.of();
        } catch (IOException e) {
            throw new IllegalStateException("全文检索失败", e);
        }
    }

    // 写入索引（rebuild=true会覆盖）
    private void indexSegments(List<TextSegment> segments, boolean rebuild) {
        if (!properties.isEnabled()) {
            return;
        }
        if (segments == null || segments.isEmpty()) {
            return;
        }
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(rebuild ? IndexWriterConfig.OpenMode.CREATE : IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (TextSegment segment : segments) {
                writer.addDocument(toDocument(segment));
            }
            writer.commit();
        } catch (IOException e) {
            throw new IllegalStateException("写入全文索引失败", e);
        }
    }

    // 构建主检索Query与元数据过滤条件
    private Query buildQuery(String queryText, FullTextFilter filter) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        try {
            QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
            builder.add(parser.parse(QueryParser.escape(queryText)), BooleanClause.Occur.MUST);
        } catch (Exception e) {
            builder.add(new TermQuery(new Term(FIELD_CONTENT, queryText)), BooleanClause.Occur.MUST);
        }

        if (filter != null) {
            if (filter.source() != null && !filter.source().isBlank()) {
                builder.add(new TermQuery(new Term(FIELD_SOURCE, filter.source())), BooleanClause.Occur.FILTER);
            }
            if (filter.version() != null && !filter.version().isBlank()) {
                builder.add(new TermQuery(new Term(FIELD_VERSION, filter.version())), BooleanClause.Occur.FILTER);
            }
            if (filter.tags() != null && !filter.tags().isEmpty()) {
                BooleanQuery.Builder tagsQuery = new BooleanQuery.Builder();
                for (String tag : filter.tags()) {
                    if (tag == null || tag.isBlank()) {
                        continue;
                    }
                    tagsQuery.add(new TermQuery(new Term(FIELD_TAGS, tag.trim())), BooleanClause.Occur.SHOULD);
                }
                if (tagsQuery.build().clauses().size() > 0) {
                    builder.add(tagsQuery.build(), BooleanClause.Occur.FILTER);
                }
            }
        }
        return builder.build();
    }

    private Document toDocument(TextSegment segment) {
        Document doc = new Document();
        doc.add(new TextField(FIELD_CONTENT, segment.text(), Field.Store.YES));
        Metadata metadata = segment.metadata();
        if (metadata != null) {
            addStringField(doc, FIELD_SOURCE, metadata.getString(FIELD_SOURCE));
            addStringField(doc, FIELD_PATH, metadata.getString(FIELD_PATH));
            addStringField(doc, FIELD_VERSION, metadata.getString(FIELD_VERSION));
            String tags = metadata.getString(FIELD_TAGS);
            if (tags != null && !tags.isBlank()) {
                String[] parts = tags.split(",");
                for (String part : parts) {
                    if (part != null && !part.isBlank()) {
                        doc.add(new StringField(FIELD_TAGS, part.trim(), Field.Store.YES));
                    }
                }
            }
        }
        return doc;
    }

    private TextSegment toSegment(Document doc) {
        Metadata metadata = new Metadata();
        String source = doc.get(FIELD_SOURCE);
        String path = doc.get(FIELD_PATH);
        String version = doc.get(FIELD_VERSION);
        if (source != null) {
            metadata.put(FIELD_SOURCE, source);
        }
        if (path != null) {
            metadata.put(FIELD_PATH, path);
        }
        if (version != null) {
            metadata.put(FIELD_VERSION, version);
        }
        String[] tags = doc.getValues(FIELD_TAGS);
        if (tags != null && tags.length > 0) {
            metadata.put(FIELD_TAGS, String.join(",", tags));
        }
        return TextSegment.from(doc.get(FIELD_CONTENT), metadata);
    }

    private void addStringField(Document doc, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        doc.add(new StringField(name, value, Field.Store.YES));
    }

    // 查询缓存：用于热点索引优化
    private QueryCache buildQueryCache() {
        int maxEntries = properties.getCacheMaxEntries();
        int maxRamMb = properties.getCacheMaxRamMb();
        if (maxEntries <= 0 || maxRamMb <= 0) {
            return null;
        }
        return new LRUQueryCache(maxEntries, maxRamMb * 1024L * 1024L);
    }

    @PreDestroy
    @Override
    public void close() {
        try {
            directory.close();
        } catch (IOException e) {
            throw new IllegalStateException("关闭全文索引目录失败", e);
        }
    }
}
