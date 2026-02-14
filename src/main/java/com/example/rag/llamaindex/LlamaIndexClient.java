package com.example.rag.llamaindex;

import com.example.rag.service.dto.UploadOptions;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexFilters;
import static com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexIngestResponse;
import static com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexIngestUrlsRequest;
import static com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexQueryRequest;
import static com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexQueryResponse;

/**
 * LlamaIndex 侧车服务 HTTP 客户端
 * 
 * 功能：通过 HTTP 与 Python 侧车服务通信
 *   - 语义检索：/query 接口
 *   - 文档入库：/ingest 接口
 *   - URL入库：/ingest/urls 接口
 *   - 健康检查：/health 接口
 * 
 * 使用场景：
 *   - 作为 LangChain4j 的替代检索引擎
 *   - 与 LangChain4j 组成 DUAL 模式双路检索
 * 
 * 依赖服务：LlamaIndex Python 侧车（FastAPI）
 * 
 * @see LlamaIndexConfig 配置类
 * @see LlamaIndexDtos DTO 定义
 */
public class LlamaIndexClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public LlamaIndexClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    public LlamaIndexQueryResponse query(LlamaIndexQueryRequest request) {
        return restTemplate.postForObject(baseUrl + "/query", request, LlamaIndexQueryResponse.class);
    }

    public LlamaIndexQueryResponse query(String question, Integer topK, UploadOptions options) {
        LlamaIndexFilters filters = null;
        if (options != null) {
            filters = new LlamaIndexFilters(options.version(), options.tags(), options.source());
        }
        return query(new LlamaIndexQueryRequest(question, topK, filters));
    }

    public LlamaIndexIngestResponse ingestUrls(List<String> urls, UploadOptions options) {
        LlamaIndexIngestUrlsRequest request = new LlamaIndexIngestUrlsRequest(
                urls,
                options == null ? null : options.version(),
                options == null ? null : options.tags(),
                options == null ? null : options.source()
        );
        return restTemplate.postForObject(baseUrl + "/ingest/urls", request, LlamaIndexIngestResponse.class);
    }

    public LlamaIndexIngestResponse ingestUploads(List<MultipartFile> files, UploadOptions options) {
        List<Resource> resources = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(this::toResource)
                .toList();
        return ingestResources(resources, options);
    }

    public LlamaIndexIngestResponse ingestResources(List<? extends Resource> resources, UploadOptions options) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        if (resources != null) {
            for (Resource resource : resources) {
                if (resource == null) {
                    continue;
                }
                body.add("files", resource);
            }
        }
        if (options != null) {
            if (options.version() != null) {
                body.add("version", options.version());
            }
            if (options.tags() != null && !options.tags().isEmpty()) {
                body.add("tags", String.join(",", options.tags()));
            }
            if (options.source() != null) {
                body.add("source", options.source());
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        return restTemplate.postForObject(baseUrl + "/ingest", request, LlamaIndexIngestResponse.class);
    }

    private ByteArrayResource toResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败: " + file.getOriginalFilename(), e);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
