package com.analyzer.api.service.knowledge;

import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.dto.knowledge.PublishKnowledgeRequest;

public interface KnowledgePublicationService {

    KnowledgeBaseVersionResponse publish(String knowledgeBaseEntryId, PublishKnowledgeRequest request);
}
