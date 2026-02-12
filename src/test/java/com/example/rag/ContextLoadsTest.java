package com.example.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = TestApplication.class)
@Import(TestBeansConfig.class)
class ContextLoadsTest {

    @Test
    void contextLoads() {
        // 仅验证 Spring 上下文可启动
    }
}
