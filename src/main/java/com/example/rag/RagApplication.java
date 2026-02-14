package com.example.rag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RAG 在线检索服务启动入口
 * 
 * 功能：提供问答检索 API 服务
 * 启动方式：mvn spring-boot:run
 * 访问地址：http://localhost:8080/api/qa
 */
@SpringBootApplication
@MapperScan("com.example.rag.mapper")
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
