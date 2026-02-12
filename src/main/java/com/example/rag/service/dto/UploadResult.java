package com.example.rag.service.dto;

import java.util.List;

// 上传入库结果
public record UploadResult(
        int ingested,
        int stored,
        List<String> storedPaths
) {
}
