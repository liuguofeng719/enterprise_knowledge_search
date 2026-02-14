package com.example.rag;

import com.example.rag.service.IngestService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * RAG 离线入库服务启动入口
 * 
 * 功能：执行文档离线入库作业，包括：
 *   - PDF/Markdown/URL 文档加载
 *   - 全文索引构建（Lucene）
 *   - 向量入库（Chroma）
 * 
 * 启动方式：mvn spring-boot:run -Dspring-boot.run.main-class=com.example.rag.IngestApplication
 * 注意：启动后自动执行入库，完成后自动退出（非 Web 服务）
 */
@SpringBootApplication
@MapperScan("com.example.rag.mapper")
public class IngestApplication {

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(IngestApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            ctx.getBean(IngestService.class).ingestAll();
        }
    }
}
