package com.example.rag;

import com.example.rag.config.RagProperties;
import com.example.rag.web.IngestController;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

// 上传控制器测试的最小启动配置
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class)
@EnableConfigurationProperties(RagProperties.class)
@Import({IngestController.class})
public class IngestTestApplication {
}
