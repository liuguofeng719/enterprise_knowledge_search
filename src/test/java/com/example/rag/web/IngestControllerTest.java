package com.example.rag.web;

import com.example.rag.service.IngestService;
import com.example.rag.service.dto.UploadOptions;
import com.example.rag.service.dto.UploadResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IngestController.class)
@ContextConfiguration(classes = com.example.rag.IngestTestApplication.class)
@Import(GlobalExceptionHandler.class)
class IngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestService ingestService;

    @Test
    void upload_shouldReturnResult() throws Exception {
        UploadResult result = new UploadResult(1, 1, List.of("data/uploads/a.md"));
        Mockito.when(ingestService.ingestUploads(Mockito.anyList(), Mockito.any(UploadOptions.class)))
                .thenReturn(result);

        MockMultipartFile file = new MockMultipartFile(
                "files", "a.md", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/ingest/upload")
                        .file(file)
                        .param("version", "v1")
                        .param("tags", "guide,api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingested").value(1))
                .andExpect(jsonPath("$.storedPaths[0]").value("data/uploads/a.md"));
    }
}
