package com.example.rag.web;

import com.example.rag.service.RagService;
import com.example.rag.service.dto.RagRequest;
import com.example.rag.service.dto.RagResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RagController.class)
@ContextConfiguration(classes = com.example.rag.TestApplication.class)
@Import(GlobalExceptionHandler.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagService ragService;

    @Test
    void ask_shouldReturnAnswer() throws Exception {
        RagResponse response = new RagResponse(
                "答案",
                List.of("证据1", "证据2"),
                List.of("来源1", "来源2")
        );
        Mockito.when(ragService.ask(Mockito.any(RagRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"如何入库？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("答案"))
                .andExpect(jsonPath("$.evidence[0]").value("证据1"));
    }

    @Test
    void ask_shouldHandleError() throws Exception {
        Mockito.when(ragService.ask(Mockito.any(RagRequest.class)))
                .thenThrow(new IllegalStateException("模拟失败"));

        mockMvc.perform(post("/api/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"如何入库？\"}"))
                .andExpect(status().is5xxServerError());
    }
}
