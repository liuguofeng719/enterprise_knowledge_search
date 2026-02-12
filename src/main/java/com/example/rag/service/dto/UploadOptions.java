package com.example.rag.service.dto;

import java.util.List;

// 上传入库可选参数
public record UploadOptions(
        String version,
        List<String> tags,
        String source
) {
}
