package com.analyzer.api.service.knowledge;

import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.dto.knowledge.UploadKnowledgeRequest;

public interface KnowledgeUploadService {

    KnowledgeBaseVersionResponse upload(UploadKnowledgeRequest request);
}
