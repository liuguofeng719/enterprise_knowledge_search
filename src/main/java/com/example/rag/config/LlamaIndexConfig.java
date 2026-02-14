package com.example.rag.config;

import com.example.rag.llamaindex.LlamaIndexClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * LlamaIndex 侧车服务配置
 * 
 * 功能：
 *   1. 配置 HTTP 客户端（RestTemplate）
 *   2. 配置连接/读取超时时间
 *   3. 初始化 LlamaIndexClient Bean
 * 
 * 依赖服务：LlamaIndex Python 侧车（默认 http://localhost:9001）
 * 
 * @see LlamaIndexClient LlamaIndex HTTP 客户端
 * @see RagProperties.LlamaIndex 配置项
 */
@Configuration
public class LlamaIndexConfig {

    @Bean
    public RestTemplate llamaIndexRestTemplate(RagProperties properties) {
        int timeoutMs = properties.getLlamaindex().getTimeoutMs();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    @Bean
    public LlamaIndexClient llamaIndexClient(RestTemplate llamaIndexRestTemplate, RagProperties properties) {
        return new LlamaIndexClient(llamaIndexRestTemplate, properties.getLlamaindex().getBaseUrl());
    }
}
