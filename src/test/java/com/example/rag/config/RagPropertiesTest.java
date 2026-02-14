package com.example.rag.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void llamaIndexProperties_shouldBind() {
        runner.withPropertyValues(
                        "rag.llamaindex.base-url=http://localhost:9001",
                        "rag.llamaindex.mode=llamaindex",
                        "rag.llamaindex.collection=llamaindex_v1",
                        "rag.llamaindex.top-k=7",
                        "rag.llamaindex.timeout-ms=9000")
                .run(context -> {
                    RagProperties properties = context.getBean(RagProperties.class);
                    assertThat(properties.getLlamaindex().getBaseUrl()).isEqualTo("http://localhost:9001");
                    assertThat(properties.getLlamaindex().getMode())
                            .isEqualTo(RagProperties.LlamaIndex.Mode.LLAMAINDEX);
                    assertThat(properties.getLlamaindex().getTopK()).isEqualTo(7);
                    assertThat(properties.getLlamaindex().getTimeoutMs()).isEqualTo(9000);
                    assertThat(properties.getLlamaindex().getCollection()).isEqualTo("llamaindex_v1");
                });
    }

    @Configuration
    @EnableConfigurationProperties(RagProperties.class)
    static class TestConfig {
    }
}
