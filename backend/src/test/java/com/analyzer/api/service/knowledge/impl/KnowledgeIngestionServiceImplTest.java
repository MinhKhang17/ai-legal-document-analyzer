package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.dto.knowledge.KnowledgeIngestionProgressRequest;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.KnowledgeIngestionJob;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.repository.knowledge.KnowledgeIngestionJobRepository;
import com.analyzer.api.service.knowledge.KnowledgeIngestNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIngestionServiceImplTest {

    @Test
    void completedCallbackStoresAuthoritativeAiDocumentId() {
        KnowledgeBaseEntryRepository entryRepository = mock(KnowledgeBaseEntryRepository.class);
        KnowledgeBaseVersionRepository versionRepository = mock(KnowledgeBaseVersionRepository.class);
        KnowledgeIngestionJobRepository jobRepository = mock(KnowledgeIngestionJobRepository.class);
        KnowledgeBaseEntry entry = KnowledgeBaseEntry.builder().id("kb-1").active(false).build();
        KnowledgeBaseVersion version = KnowledgeBaseVersion.builder()
                .id("kbv-1").knowledgeBaseEntry(entry).status(KnowledgeStatus.PROCESSING)
                .ingestStatus(KnowledgeStatus.PROCESSING).active(false).build();
        KnowledgeIngestionJob job = KnowledgeIngestionJob.builder()
                .id("job-1").knowledgeBaseVersion(version).status(KnowledgeStatus.PROCESSING).build();
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any(KnowledgeIngestionJob.class))).then(returnsFirstArg());
        KnowledgeIngestionServiceImpl service = new KnowledgeIngestionServiceImpl(
                entryRepository,
                versionRepository,
                jobRepository,
                mock(UserRepository.class),
                mock(KnowledgeIngestNotificationService.class),
                mock(ApplicationEventPublisher.class));
        KnowledgeIngestionProgressRequest request = new KnowledgeIngestionProgressRequest();
        request.setStatus("INGESTED");
        request.setProgressPercent(100);
        request.setChunkCount(4);
        request.setNeo4jDocumentId("actual-ai-document-id");

        service.updateProgress("job-1", request);

        assertEquals("actual-ai-document-id", version.getNeo4jDocumentId());
        assertEquals(KnowledgeStatus.INGESTED, version.getIngestStatus());
        verify(versionRepository).save(version);
        verify(entryRepository).save(entry);
    }
}
