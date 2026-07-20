package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.knowledge.KnowledgeBaseIngestedDocumentResponse;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIngestedDocumentsServiceImplTest {

    @Test
    void aiSnapshotsAreLoadedInPagesWithinAiServiceLimit() {
        KnowledgeBaseEntryRepository entryRepository = mock(KnowledgeBaseEntryRepository.class);
        KnowledgeBaseVersionRepository versionRepository = mock(KnowledgeBaseVersionRepository.class);
        KnowledgeBaseAiClient aiClient = mock(KnowledgeBaseAiClient.class);
        KnowledgeBaseEntry entry = KnowledgeBaseEntry.builder()
                .id("kb-1").code("LAW").title("Law").build();
        when(entryRepository.findById("kb-1")).thenReturn(Optional.of(entry));
        when(versionRepository.findByKnowledgeBaseEntryIdOrderByVersionNoDesc("kb-1"))
                .thenReturn(List.of());
        when(aiClient.getIngestedDocuments("kb-1", null, null, null, 0, 100))
                .thenReturn(emptyPage(0, 2));
        when(aiClient.getIngestedDocuments("kb-1", null, null, null, 1, 100))
                .thenReturn(emptyPage(1, 2));
        KnowledgeIngestedDocumentsServiceImpl service = new KnowledgeIngestedDocumentsServiceImpl(
                entryRepository, versionRepository, aiClient);

        service.getIngestedDocuments("kb-1", null, null, null, 0, 10);

        verify(aiClient).getIngestedDocuments("kb-1", null, null, null, 0, 100);
        verify(aiClient).getIngestedDocuments("kb-1", null, null, null, 1, 100);
    }

    private PageResponse<KnowledgeBaseIngestedDocumentResponse> emptyPage(int page, int totalPages) {
        return PageResponse.<KnowledgeBaseIngestedDocumentResponse>builder()
                .items(List.of())
                .page(page)
                .size(100)
                .totalItems(101)
                .totalPages(totalPages)
                .build();
    }
}
