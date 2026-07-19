package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionJobResponse;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionProgressRequest;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.service.knowledge.KnowledgeIngestionService;
import com.analyzer.api.service.knowledge.KnowledgeUploadService;
import com.analyzer.api.service.knowledge.event.KnowledgeIngestionDispatchRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionDispatcherTest {

    @Mock
    private KnowledgeBaseAiClient aiClient;
    @Mock
    private KnowledgeUploadService uploadService;
    @Mock
    private KnowledgeIngestionService ingestionService;
    @Mock
    private KnowledgeBaseEntryRepository entryRepository;
    @Mock
    private Resource sourceFile;

    private KnowledgeIngestionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new KnowledgeIngestionDispatcher(aiClient, uploadService, ingestionService, entryRepository);
        ReflectionTestUtils.setField(dispatcher, "callbackBaseUrl", "http://backend:8080/");
    }

    @Test
    void dispatchesStoredSourceFileFromBackendToAiService() {
        when(ingestionService.getJob("job_1")).thenReturn(processingJob());
        when(entryRepository.findById("kb_1"))
                .thenReturn(Optional.of(KnowledgeBaseEntry.builder().id("kb_1").title("Bo luat dan su").build()));
        when(uploadService.loadSourceFile("kb_1")).thenReturn(sourceFile);

        dispatcher.dispatch(new KnowledgeIngestionDispatchRequested("kb_1", "job_1", 12L));

        verify(aiClient).submitIngest(
                sourceFile,
                "Bo luat dan su",
                "job_1",
                "http://backend:8080/api/internal/knowledge-ingestion/job_1/progress",
                "kb_1",
                12L);
        verify(ingestionService, never()).updateProgress(any(), any());
    }

    @Test
    void marksJobFailedWhenAiSubmissionFails() {
        when(ingestionService.getJob("job_1")).thenReturn(processingJob());
        when(entryRepository.findById("kb_1"))
                .thenReturn(Optional.of(KnowledgeBaseEntry.builder().id("kb_1").title("Bo luat dan su").build()));
        when(uploadService.loadSourceFile("kb_1")).thenReturn(sourceFile);
        doThrow(new IllegalStateException("AI service unavailable"))
                .when(aiClient).submitIngest(any(), any(), any(), any(), any(), any());

        dispatcher.dispatch(new KnowledgeIngestionDispatchRequested("kb_1", "job_1", 12L));

        ArgumentCaptor<KnowledgeIngestionProgressRequest> requestCaptor =
                ArgumentCaptor.forClass(KnowledgeIngestionProgressRequest.class);
        verify(ingestionService).updateProgress(org.mockito.ArgumentMatchers.eq("job_1"), requestCaptor.capture());
        assertEquals("FAILED", requestCaptor.getValue().getStatus());
        assertEquals(100, requestCaptor.getValue().getProgressPercent());
        assertEquals("AI service unavailable", requestCaptor.getValue().getErrorMessage());
    }

    private KnowledgeIngestionJobResponse processingJob() {
        return KnowledgeIngestionJobResponse.builder()
                .id("job_1")
                .status(KnowledgeStatus.PROCESSING)
                .progressPercent(10)
                .build();
    }
}
