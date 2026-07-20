package com.analyzer.api.service.knowledge.event;

public record KnowledgeIngestionDispatchRequested(
        String knowledgeBaseEntryId,
        String jobId,
        Long adminId) {
}
