package com.analyzer.api.service.knowledge;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.knowledge.KnowledgeBaseIngestedDocumentResponse;

public interface KnowledgeIngestedDocumentsService {

    PageResponse<KnowledgeBaseIngestedDocumentResponse> getIngestedDocuments(
            String knowledgeBaseEntryId,
            String keyword,
            String ingestStatus,
            String visibility,
            int page,
            int size);

    PageResponse<KnowledgeBaseIngestedDocumentResponse> searchIngestedDocuments(
            String title, String category, Integer version, String source, String status,
            java.time.LocalDateTime createdFrom, java.time.LocalDateTime createdTo, int page, int size);
}
