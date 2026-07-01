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
}
