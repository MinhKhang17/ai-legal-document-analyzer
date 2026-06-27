package com.analyzer.api.service.knowledge;

import com.analyzer.api.dto.knowledge.IngestKnowledgeRequest;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionJobResponse;

public interface KnowledgeIngestionService {

    KnowledgeIngestionJobResponse ingest(String knowledgeBaseEntryId, IngestKnowledgeRequest request);
}
