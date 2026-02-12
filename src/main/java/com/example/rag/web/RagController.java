package com.example.rag.web;

import com.example.rag.service.RagService;
import com.example.rag.service.dto.RagRequest;
import com.example.rag.service.dto.RagResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
