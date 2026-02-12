package com.example.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

// 评测集加载器（JSON）
public class EvaluationDatasetLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 读取评测数据集
    public EvaluationDataset load(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return objectMapper.readValue(in, EvaluationDataset.class);
        } catch (IOException e) {
            throw new IllegalStateException("加载评测集失败: " + path, e);
        }
    }
}
