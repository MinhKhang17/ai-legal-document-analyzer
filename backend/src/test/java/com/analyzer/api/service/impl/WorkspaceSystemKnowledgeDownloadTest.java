package com.analyzer.api.service.impl;
import com.analyzer.api.service.workspace.impl.WorkspaceServiceImpl;

import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.document.DocumentRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.workspace.WorkspaceRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.notification.EmailService;
import com.analyzer.api.service.policy.PolicyAcceptanceService;
import com.analyzer.api.service.subscription.SubscriptionQuotaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceSystemKnowledgeDownloadTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock DocumentRepository documentRepository;
    @Mock UserRepository userRepository;
    @Mock SubscriptionQuotaService subscriptionQuotaService;
    @Mock EmailService emailService;
    @Mock PolicyAcceptanceService policyAcceptanceService;
    @Mock KnowledgeBaseVersionRepository knowledgeBaseVersionRepository;
    @TempDir Path uploadRoot;

    @Test
    void downloadsPublishedKnowledgeSourceUsingDatabaseStoragePathAndFilenameStem() throws Exception {
        Path source = uploadRoot.resolve("knowledge-source/kb-1/kbv-1/100.2015.QH13.pdf");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "test legal source");
        KnowledgeBaseVersion version = KnowledgeBaseVersion.builder()
                .originalFileName("100.2015.QH13.pdf")
                .sourceStoragePath(source.toString())
                .status(KnowledgeStatus.PUBLIC)
                .visibility(KnowledgeVisibility.PUBLIC)
                .active(true)
                .build();
        when(knowledgeBaseVersionRepository.findByStatusAndVisibilityAndActiveTrueOrderByCreatedAtDesc(
                KnowledgeStatus.PUBLIC, KnowledgeVisibility.PUBLIC)).thenReturn(List.of(version));

        Resource resource = service().downloadSystemDocumentFile("100.2015.QH13");

        assertEquals("100.2015.QH13.pdf", resource.getFilename());
    }

    @Test
    void resolvesLegacyStorageDirectoryToOriginalSourceFile() throws Exception {
        Path legacyDirectory = uploadRoot.resolve("knowledge_base");
        Files.createDirectories(legacyDirectory);
        Files.writeString(legacyDirectory.resolve("100.2015.QH13.pdf"), "test legal source");
        KnowledgeBaseVersion version = KnowledgeBaseVersion.builder()
                .originalFileName("100.2015.QH13.pdf")
                .sourceStoragePath(legacyDirectory.toString())
                .status(KnowledgeStatus.PUBLIC)
                .visibility(KnowledgeVisibility.PUBLIC)
                .active(true)
                .build();
        when(knowledgeBaseVersionRepository.findByStatusAndVisibilityAndActiveTrueOrderByCreatedAtDesc(
                KnowledgeStatus.PUBLIC, KnowledgeVisibility.PUBLIC)).thenReturn(List.of(version));

        Resource resource = service().downloadSystemDocumentFile("100.2015.QH13");

        assertEquals("100.2015.QH13.pdf", resource.getFilename());
    }

    @Test
    void missingPublishedKnowledgeSourceReturnsNotFound() {
        when(knowledgeBaseVersionRepository.findByStatusAndVisibilityAndActiveTrueOrderByCreatedAtDesc(
                KnowledgeStatus.PUBLIC, KnowledgeVisibility.PUBLIC)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service().downloadSystemDocumentFile("missing-document"));
    }

    private WorkspaceServiceImpl service() {
        WorkspaceServiceImpl service = new WorkspaceServiceImpl(
                workspaceRepository, documentRepository, userRepository, subscriptionQuotaService,
                emailService, policyAcceptanceService, knowledgeBaseVersionRepository);
        ReflectionTestUtils.setField(service, "uploadRoot", uploadRoot.toString());
        return service;
    }
}
