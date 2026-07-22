package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.dto.knowledge.KnowledgeReviewRequest;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.KnowledgeReviewDecision;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

class KnowledgeReviewServiceImplTest {

    @Test
    void approvePublishesAndActivatesTheReviewedVersionImmediately() {
        Fixture fixture = fixture();
        when(fixture.aiClient.updateLifecycle("kb-1", "neo-doc-1", true)).thenReturn(true);

        fixture.service.review("kb-1", approveRequest());

        assertEquals(KnowledgeStatus.PUBLIC, fixture.version.getStatus());
        assertEquals(KnowledgeVisibility.PUBLIC, fixture.version.getVisibility());
        assertTrue(fixture.version.getActive());
        assertEquals(KnowledgeStatus.PUBLIC, fixture.entry.getCurrentStatus());
        assertTrue(fixture.entry.getActive());
        assertEquals(fixture.admin, fixture.version.getPublishedBy());
        verify(fixture.versionRepository).save(fixture.version);
        verify(fixture.entryRepository).save(fixture.entry);
    }

    @Test
    void approveDoesNotPublishPostgresWhenNeo4jLifecycleCannotBeUpdated() {
        Fixture fixture = fixture();
        when(fixture.aiClient.updateLifecycle("kb-1", "neo-doc-1", true)).thenReturn(false);

        assertThrows(ConflictException.class, () -> fixture.service.review("kb-1", approveRequest()));

        verify(fixture.aiClient).updateLifecycle("kb-1", "neo-doc-1", true);
        verify(fixture.aiClient, never()).updateLifecycle("kb-1", "kb-1", true);
        verify(fixture.versionRepository, never()).save(fixture.version);
        verify(fixture.entryRepository, never()).save(fixture.entry);
    }

    private KnowledgeReviewRequest approveRequest() {
        KnowledgeReviewRequest request = new KnowledgeReviewRequest();
        request.setDecision(KnowledgeReviewDecision.APPROVE);
        request.setNote("Approved");
        return request;
    }

    private Fixture fixture() {
        KnowledgeBaseEntryRepository entryRepository = mock(KnowledgeBaseEntryRepository.class);
        KnowledgeBaseVersionRepository versionRepository = mock(KnowledgeBaseVersionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        KnowledgeBaseAiClient aiClient = mock(KnowledgeBaseAiClient.class);
        User admin = User.builder().id(1L).build();
        KnowledgeBaseEntry entry = KnowledgeBaseEntry.builder()
                .id("kb-1").code("LAW").title("Law").category("LEGAL_SOURCE")
                .currentVersionNo(1).currentStatus(KnowledgeStatus.INGESTED)
                .active(false).createdBy(admin).build();
        KnowledgeBaseVersion version = KnowledgeBaseVersion.builder()
                .id("kbv-1").knowledgeBaseEntry(entry).versionNo(1)
                .status(KnowledgeStatus.INGESTED).ingestStatus(KnowledgeStatus.INGESTED)
                .visibility(KnowledgeVisibility.PRIVATE).active(false)
                .neo4jDocumentId("neo-doc-1").extractedContent("").build();
        when(entryRepository.findById("kb-1")).thenReturn(Optional.of(entry));
        when(versionRepository.findByKnowledgeBaseEntryIdAndVersionNo("kb-1", 1)).thenReturn(Optional.of(version));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(versionRepository.save(org.mockito.ArgumentMatchers.any(KnowledgeBaseVersion.class))).then(returnsFirstArg());
        when(entryRepository.save(org.mockito.ArgumentMatchers.any(KnowledgeBaseEntry.class))).then(returnsFirstArg());
        KnowledgeReviewServiceImpl service = new KnowledgeReviewServiceImpl(
                entryRepository, versionRepository, userRepository, aiClient);
        return new Fixture(service, entryRepository, versionRepository, aiClient, entry, version, admin);
    }

    private record Fixture(
            KnowledgeReviewServiceImpl service,
            KnowledgeBaseEntryRepository entryRepository,
            KnowledgeBaseVersionRepository versionRepository,
            KnowledgeBaseAiClient aiClient,
            KnowledgeBaseEntry entry,
            KnowledgeBaseVersion version,
            User admin) {
    }
}
