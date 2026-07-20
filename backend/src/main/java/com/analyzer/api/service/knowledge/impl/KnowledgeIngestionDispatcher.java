package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionJobResponse;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionProgressRequest;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.service.knowledge.KnowledgeIngestionService;
import com.analyzer.api.service.knowledge.KnowledgeUploadService;
import com.analyzer.api.service.knowledge.event.KnowledgeIngestionDispatchRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIngestionDispatcher {

    private final KnowledgeBaseAiClient aiClient;
    private final KnowledgeUploadService knowledgeUploadService;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final KnowledgeBaseEntryRepository entryRepository;

    @Value("${app.api.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(KnowledgeIngestionDispatchRequested event) {
        try {
            KnowledgeIngestionJobResponse job = knowledgeIngestionService.getJob(event.jobId());
            if (job.getStatus() != KnowledgeStatus.PROCESSING) {
                log.info("Skip AI ingest dispatch because job is no longer processing jobId={} status={}",
                        event.jobId(), job.getStatus());
                return;
            }

            KnowledgeBaseEntry entry = entryRepository.findById(event.knowledgeBaseEntryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Khong tim thay knowledge base ID: " + event.knowledgeBaseEntryId()));
            Resource sourceFile = knowledgeUploadService.loadSourceFile(event.knowledgeBaseEntryId());
            String callbackUrl = callbackBaseUrl.replaceAll("/+$", "")
                    + "/api/internal/knowledge-ingestion/" + event.jobId() + "/progress";

            aiClient.submitIngest(
                    sourceFile,
                    entry.getTitle(),
                    event.jobId(),
                    callbackUrl,
                    event.knowledgeBaseEntryId(),
                    event.adminId());
            log.info("AI knowledge ingest accepted jobId={} knowledgeBaseId={}",
                    event.jobId(), event.knowledgeBaseEntryId());
        } catch (Exception exception) {
            log.warn("Unable to submit knowledge ingest to AI service jobId={}: {}",
                    event.jobId(), exception.getMessage());
            markFailed(event.jobId(), exception);
        }
    }

    private void markFailed(String jobId, Exception exception) {
        try {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "Unable to submit ingest job to AI service"
                    : exception.getMessage();
            KnowledgeIngestionProgressRequest request = new KnowledgeIngestionProgressRequest();
            request.setStatus(KnowledgeStatus.FAILED.name());
            request.setProgressPercent(100);
            request.setErrorMessage(message);
            knowledgeIngestionService.updateProgress(jobId, request);
        } catch (Exception updateException) {
            log.error("Unable to mark knowledge ingest job as failed jobId={}: {}",
                    jobId, updateException.getMessage());
        }
    }
}
