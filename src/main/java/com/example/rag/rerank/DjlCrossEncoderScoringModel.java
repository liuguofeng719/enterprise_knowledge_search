package com.example.rag.rerank;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.CrossEncoderTranslator;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.util.StringPair;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// DJL交叉编码器实现，基于HuggingFace模型
public class DjlCrossEncoderScoringModel implements ScoringModel, Closeable {

    private final ZooModel<StringPair, float[]> model;

    public DjlCrossEncoderScoringModel(String modelId, boolean includeTokenTypes, boolean sigmoid) {
        this.model = loadModel(modelId, null, includeTokenTypes, sigmoid);
    }

    public DjlCrossEncoderScoringModel(Path modelPath, boolean includeTokenTypes, boolean sigmoid) {
        this.model = loadModel(null, modelPath, includeTokenTypes, sigmoid);
    }

    @Override
    // 批量打分
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return Response.from(List.of());
        }
        List<StringPair> inputs = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            inputs.add(new StringPair(query, segment.text()));
        }
        try (Predictor<StringPair, float[]> predictor = model.newPredictor()) {
            List<float[]> outputs = predictor.batchPredict(inputs);
            List<Double> scores = new ArrayList<>(outputs.size());
            for (float[] output : outputs) {
                if (output == null || output.length == 0) {
                    scores.add(0.0);
                } else {
                    scores.add((double) output[0]);
                }
            }
            return Response.from(scores);
        } catch (TranslateException e) {
            throw new IllegalStateException("交叉重排推理失败", e);
        }
    }

    @Override
    public void close() throws IOException {
        model.close();
    }

    private ZooModel<StringPair, float[]> loadModel(String modelId,
                                                    Path modelPath,
                                                    boolean includeTokenTypes,
                                                    boolean sigmoid) {
        try {
            HuggingFaceTokenizer tokenizer = modelPath == null
                    ? HuggingFaceTokenizer.newInstance(modelId)
                    : HuggingFaceTokenizer.newInstance(modelPath);
            CrossEncoderTranslator translator = CrossEncoderTranslator.builder(tokenizer)
                    .optIncludeTokenTypes(includeTokenTypes)
                    .optSigmoid(sigmoid)
                    .build();
            Criteria.Builder<StringPair, float[]> builder = Criteria.builder()
                    .setTypes(StringPair.class, float[].class)
                    .optEngine("PyTorch")
                    .optTranslator(translator);
            if (modelPath != null) {
                builder.optModelPath(modelPath);
            } else {
                builder.optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelId);
            }
            return builder.build().loadModel();
        } catch (Exception e) {
            throw new IllegalStateException("加载交叉重排模型失败", e);
        }
    }
}
