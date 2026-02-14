package com.example.rag.config;

import com.example.rag.llamaindex.LlamaIndexClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

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
