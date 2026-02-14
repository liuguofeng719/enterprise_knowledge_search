package com.example.rag.web;

import com.example.rag.service.RagService;
import com.example.rag.service.dto.RagRequest;
import com.example.rag.service.dto.RagResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 问答检索 API 控制器
 * 
 * 接口：POST /api/qa
 * 
 * 请求参数：
 *   - question：问题内容（必填）
 *   - version：版本过滤（可选）
 *   - tags：标签过滤（可选）
 *   - source：来源过滤（可选）
 *   - keywords：关键词增强（可选）
 *   - topK：返回数量（可选，默认5）
 *   - minScore：最小相似度（可选，默认0.2）
 * 
 * @see RagRequest 请求DTO
 * @see RagResponse 响应DTO
 */
@RestController
@RequestMapping("/api")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/qa")
    public RagResponse ask(@Valid @RequestBody RagRequest request) {
        return ragService.ask(request);
    }
}
