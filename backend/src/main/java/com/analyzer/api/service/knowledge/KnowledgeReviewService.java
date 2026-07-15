package com.analyzer.api.service.knowledge;

import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.dto.knowledge.KnowledgeReviewRequest;

public interface KnowledgeReviewService {

    KnowledgeBaseVersionResponse review(String knowledgeBaseEntryId, KnowledgeReviewRequest request);
}
