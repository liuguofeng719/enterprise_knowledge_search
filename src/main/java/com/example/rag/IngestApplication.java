package com.example.rag;

import com.example.rag.service.IngestService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@MapperScan("com.example.rag.mapper")
public class IngestApplication {

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(IngestApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            // 启动后执行入库作业
            ctx.getBean(IngestService.class).ingestAll();
        }
    }
}
