package com.example.rag;

import com.example.rag.config.RagProperties;
import com.example.rag.service.RagService;
import com.example.rag.web.RagController;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class)
@EnableConfigurationProperties(RagProperties.class)
@Import({RagService.class, RagController.class})
public class TestApplication {
}
