package com.example.rag.llamaindex;

import com.example.rag.service.dto.UploadOptions;
import com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexFilters;
import com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexIngestResponse;
import com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexQueryRequest;
import com.example.rag.llamaindex.LlamaIndexDtos.LlamaIndexQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LlamaIndexClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private LlamaIndexClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new LlamaIndexClient(restTemplate, "http://localhost:9001");
    }

    @Test
    void query_shouldSendRequestAndParseResponse() {
        mockServer.expect(requestTo("http://localhost:9001/query"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"items":[{"text":"t","score":0.8,"metadata":{"source":"upload","path":"/a"}}]}
                        """, MediaType.APPLICATION_JSON));

        LlamaIndexQueryResponse response = client.query(
                new LlamaIndexQueryRequest("q", 5, new LlamaIndexFilters(null, null, null)));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).text()).isEqualTo("t");
        mockServer.verify();
    }

    @Test
    void ingestUrls_shouldSendRequestAndParseResponse() {
        mockServer.expect(requestTo("http://localhost:9001/ingest/urls"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"ingested":1,"stored":1,"failed":[]}
                        """, MediaType.APPLICATION_JSON));

        LlamaIndexIngestResponse response = client.ingestUrls(
                List.of("http://example.com/a"),
                new UploadOptions("v1", List.of("guide"), "web"));

        assertThat(response.ingested()).isEqualTo(1);
        assertThat(response.stored()).isEqualTo(1);
        assertThat(response.failed()).isEmpty();
        mockServer.verify();
    }
}
