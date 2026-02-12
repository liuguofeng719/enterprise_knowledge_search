package com.example.rag;

import com.example.rag.eval.EvaluationDataset;
import com.example.rag.eval.EvaluationDatasetLoader;
import com.example.rag.eval.EvaluationReport;
import com.example.rag.eval.EvaluationService;
import com.example.rag.service.RagService;
import com.example.rag.service.dto.RagRequest;
import dev.langchain4j.data.segment.TextSegment;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;

// 离线评测入口：加载评测集并输出指标
@SpringBootApplication
@MapperScan("com.example.rag.mapper")
public class EvaluationApplication {

    // 参数：评测集路径, TopK
    public static void main(String[] args) {
        String datasetPath = args.length > 0 ? args[0] : "samples/eval/eval-set.json";
        int topK = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(EvaluationApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            RagService ragService = ctx.getBean(RagService.class);
            EvaluationService evaluationService = ctx.getBean(EvaluationService.class);
            EvaluationDataset dataset = new EvaluationDatasetLoader().load(Path.of(datasetPath));
            EvaluationReport report = evaluationService.evaluate(dataset, (question, k) -> {
                RagRequest request = new RagRequest(question, k, null, null, null, null, null);
                List<String> evidence = ragService.ask(request).evidence();
                return evidence.stream().map(TextSegment::from).toList();
            }, topK);
            System.out.printf("total=%d hit=%d hitRate=%.4f recall@k=%.4f%n",
                    report.total(), report.hitCount(), report.hitRate(), report.recallAtK());
        }
    }
}
