package com.example.rag.service;

import com.example.rag.mapper.KbDocumentMapper;
import com.example.rag.model.KbDocument;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DocumentMetadataService {

    private final KbDocumentMapper mapper;

    public DocumentMetadataService(KbDocumentMapper mapper) {
        this.mapper = mapper;
    }

    public void save(String source, String path, String version, String tags, String status) {
        KbDocument doc = new KbDocument();
        doc.setSource(source);
        doc.setPath(path);
        doc.setVersion(version);
        doc.setTags(tags);
        doc.setStatus(status);
        doc.setUpdatedAt(LocalDateTime.now());
        mapper.insert(doc);
    }
}
