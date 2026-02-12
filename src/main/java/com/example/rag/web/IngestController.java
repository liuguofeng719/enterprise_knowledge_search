package com.example.rag.web;

import com.example.rag.service.IngestService;
import com.example.rag.service.dto.UploadOptions;
import com.example.rag.service.dto.UploadResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

// 文档上传入库接口
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    // 上传PDF/Markdown并触发入库
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResult upload(@RequestPart("files") List<MultipartFile> files,
                               @RequestParam(value = "version", required = false) String version,
                               @RequestParam(value = "tags", required = false) String tags,
                               @RequestParam(value = "source", required = false) String source) {
        UploadOptions options = new UploadOptions(version, parseTags(tags), source);
        return ingestService.ingestUploads(files, options);
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(",");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            tags.add(part.trim());
        }
        return tags;
    }
}
