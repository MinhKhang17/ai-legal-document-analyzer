package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileRequest;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileResponse;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.knowledge.KnowledgeIngestionService;
import com.analyzer.api.service.knowledge.KnowledgeUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServerBulkServiceImplTest {

    @TempDir
    Path inputDir;

    @Test
    void dryRunChecksPostgresWithoutCreatingMetadataOrCallingAi() throws Exception {
        KnowledgeUploadService uploadService = mock(KnowledgeUploadService.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        KnowledgeBaseVersionRepository versionRepository = mock(KnowledgeBaseVersionRepository.class);
        KnowledgeServerBulkServiceImpl service = service(uploadService, ingestionService, versionRepository);
        Files.writeString(inputDir.resolve("law.txt"), "legal content");
        when(versionRepository.findFirstBySourceFileHashOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());

        ServerBulkIngestFileRequest request = new ServerBulkIngestFileRequest();
        request.setRelativePath("law.txt");
        request.setDryRun(true);

        ServerBulkIngestFileResponse response = service.ingestFile(request, 1L);

        assertEquals("DRY_RUN", response.getStatus());
        assertEquals("NOT_WRITTEN", response.getPostgresStatus());
        assertEquals("NOT_WRITTEN", response.getNeo4jStatus());
        verify(uploadService, never()).upload(org.mockito.ArgumentMatchers.any());
        verify(ingestionService, never()).ingest(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void completedHashIsSkippedFromPostgres() throws Exception {
        KnowledgeUploadService uploadService = mock(KnowledgeUploadService.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        KnowledgeBaseVersionRepository versionRepository = mock(KnowledgeBaseVersionRepository.class);
        KnowledgeServerBulkServiceImpl service = service(uploadService, ingestionService, versionRepository);
        Files.writeString(inputDir.resolve("law.txt"), "legal content");
        KnowledgeBaseEntry entry = KnowledgeBaseEntry.builder().id("kb-1").code("LAW").build();
        KnowledgeBaseVersion version = KnowledgeBaseVersion.builder()
                .id("kbv-1").knowledgeBaseEntry(entry).ingestStatus(KnowledgeStatus.INGESTED)
                .neo4jDocumentId("neo-1").chunkCount(4).build();
        when(versionRepository.findFirstBySourceFileHashOrderByCreatedAtDesc(anyString())).thenReturn(Optional.of(version));

        ServerBulkIngestFileRequest request = new ServerBulkIngestFileRequest();
        request.setRelativePath("law.txt");

        ServerBulkIngestFileResponse response = service.ingestFile(request, 1L);

        assertEquals("SKIPPED", response.getStatus());
        assertEquals("duplicate_hash", response.getErrorMessage());
        verify(uploadService, never()).upload(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPathTraversalBeforeReadingFile() {
        KnowledgeServerBulkServiceImpl service = service(
                mock(KnowledgeUploadService.class), mock(KnowledgeIngestionService.class),
                mock(KnowledgeBaseVersionRepository.class));
        ServerBulkIngestFileRequest request = new ServerBulkIngestFileRequest();
        request.setRelativePath("../outside.pdf");

        assertThrows(IllegalArgumentException.class, () -> service.ingestFile(request, 1L));
    }

    private KnowledgeServerBulkServiceImpl service(
            KnowledgeUploadService uploadService,
            KnowledgeIngestionService ingestionService,
            KnowledgeBaseVersionRepository versionRepository) {
        KnowledgeServerBulkServiceImpl service = new KnowledgeServerBulkServiceImpl(
                uploadService,
                ingestionService,
                mock(KnowledgeBaseEntryRepository.class),
                versionRepository,
                mock(UserRepository.class),
                mock(KnowledgeBaseAiClient.class));
        ReflectionTestUtils.setField(service, "configuredBulkInputDir", inputDir.toString());
        return service;
    }
}
