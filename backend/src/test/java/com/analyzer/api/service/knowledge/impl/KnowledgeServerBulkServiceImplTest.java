package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileRequest;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileResponse;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
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
import static org.mockito.ArgumentMatchers.any;
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
                .status(KnowledgeStatus.PUBLIC).visibility(KnowledgeVisibility.PUBLIC).active(true)
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
    void completedPrivateDuplicateIsPublishedBeforeItIsSkipped() throws Exception {
        KnowledgeUploadService uploadService = mock(KnowledgeUploadService.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        KnowledgeBaseVersionRepository versionRepository = mock(KnowledgeBaseVersionRepository.class);
        KnowledgeBaseEntryRepository entryRepository = mock(KnowledgeBaseEntryRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        KnowledgeBaseAiClient aiClient = mock(KnowledgeBaseAiClient.class);
        KnowledgeServerBulkServiceImpl service = new KnowledgeServerBulkServiceImpl(
                uploadService, ingestionService, entryRepository, versionRepository, userRepository, aiClient);
        ReflectionTestUtils.setField(service, "configuredBulkInputDir", inputDir.toString());
        Files.writeString(inputDir.resolve("law.txt"), "legal content");
        User admin = User.builder().id(1L).build();
        KnowledgeBaseEntry entry = KnowledgeBaseEntry.builder().id("kb-1").code("LAW").active(false).build();
        KnowledgeBaseVersion version = KnowledgeBaseVersion.builder()
                .id("kbv-1").knowledgeBaseEntry(entry).status(KnowledgeStatus.INGESTED)
                .ingestStatus(KnowledgeStatus.INGESTED).visibility(KnowledgeVisibility.PRIVATE).active(false)
                .sourceFileHash("file-hash-must-not-be-used-as-document-id")
                .neo4jDocumentId("neo-1").chunkCount(4).build();
        when(versionRepository.findFirstBySourceFileHashOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(version));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(aiClient.updateLifecycle("kb-1", "neo-1", true)).thenReturn(true);
        when(versionRepository.save(any(KnowledgeBaseVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServerBulkIngestFileRequest request = new ServerBulkIngestFileRequest();
        request.setRelativePath("law.txt");

        ServerBulkIngestFileResponse response = service.ingestFile(request, 1L);

        assertEquals("SKIPPED", response.getStatus());
        assertEquals("PUBLIC", response.getPostgresStatus());
        assertEquals(KnowledgeStatus.PUBLIC, version.getStatus());
        assertEquals(KnowledgeVisibility.PUBLIC, version.getVisibility());
        assertEquals(true, version.getActive());
        assertEquals(true, entry.getActive());
        verify(aiClient).updateLifecycle("kb-1", "neo-1", true);
        verify(aiClient, never()).updateLifecycle("kb-1", "kb-1", true);
        verify(aiClient, never()).updateLifecycle("kb-1", "file-hash-must-not-be-used-as-document-id", true);
        verify(entryRepository).save(entry);
        verify(uploadService, never()).upload(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completedDuplicateWithoutActualAiDocumentIdFailsAndStaysPrivate() throws Exception {
        KnowledgeUploadService uploadService = mock(KnowledgeUploadService.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        KnowledgeBaseVersionRepository versionRepository = mock(KnowledgeBaseVersionRepository.class);
        KnowledgeBaseEntryRepository entryRepository = mock(KnowledgeBaseEntryRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        KnowledgeBaseAiClient aiClient = mock(KnowledgeBaseAiClient.class);
        KnowledgeServerBulkServiceImpl service = new KnowledgeServerBulkServiceImpl(
                uploadService, ingestionService, entryRepository, versionRepository, userRepository, aiClient);
        ReflectionTestUtils.setField(service, "configuredBulkInputDir", inputDir.toString());
        Files.writeString(inputDir.resolve("missing-id.txt"), "legal content");
        User admin = User.builder().id(1L).build();
        KnowledgeBaseEntry entry = KnowledgeBaseEntry.builder().id("kb-2").code("LAW2").active(false).build();
        KnowledgeBaseVersion version = KnowledgeBaseVersion.builder()
                .id("kbv-2").knowledgeBaseEntry(entry).status(KnowledgeStatus.INGESTED)
                .ingestStatus(KnowledgeStatus.INGESTED).visibility(KnowledgeVisibility.PRIVATE).active(false)
                .sourceFileHash("legacy-file-hash").build();
        when(versionRepository.findFirstBySourceFileHashOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(version));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        ServerBulkIngestFileRequest request = new ServerBulkIngestFileRequest();
        request.setRelativePath("missing-id.txt");

        ServerBulkIngestFileResponse response = service.ingestFile(request, 1L);

        assertEquals("FAILED", response.getStatus());
        assertEquals("neo4j_lifecycle_update_failed", response.getErrorMessage());
        assertEquals(KnowledgeStatus.INGESTED, version.getStatus());
        assertEquals(KnowledgeVisibility.PRIVATE, version.getVisibility());
        assertEquals(false, version.getActive());
        assertEquals(false, entry.getActive());
        verify(aiClient, never()).updateLifecycle(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(versionRepository, never()).save(version);
        verify(entryRepository, never()).save(entry);
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
