package com.analyzer.api.service.knowledge;

import com.analyzer.api.dto.knowledge.ArchiveKnowledgeRequest;
import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;

public interface KnowledgeArchiveService {

    KnowledgeBaseVersionResponse archive(String knowledgeBaseEntryId, ArchiveKnowledgeRequest request);
}
