package com.analyzer.api.service.knowledge;

import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.KnowledgeIngestionJob;

import java.time.LocalDateTime;

public interface KnowledgeIngestNotificationService {
    void notifyFirstSuccessfulIngest(
            KnowledgeIngestionJob job,
            KnowledgeBaseEntry entry,
            KnowledgeBaseVersion version,
            LocalDateTime ingestedAt);
}
