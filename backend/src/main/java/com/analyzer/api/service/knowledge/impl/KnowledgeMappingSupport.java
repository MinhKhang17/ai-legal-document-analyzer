package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.dto.knowledge.KnowledgeBaseEntryResponse;
import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionJobResponse;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.KnowledgeIngestionJob;

final class KnowledgeMappingSupport {

    private KnowledgeMappingSupport() {
    }

    static KnowledgeBaseEntryResponse toEntryResponse(KnowledgeBaseEntry entry) {
        return KnowledgeBaseEntryResponse.builder()
                .id(entry.getId())
                .code(entry.getCode())
                .title(entry.getTitle())
                .category(entry.getCategory())
                .scope(entry.getScope())
                .currentVersionNo(entry.getCurrentVersionNo())
                .currentStatus(entry.getCurrentStatus())
                .active(entry.getActive())
                .createdById(entry.getCreatedBy().getId())
                .workspaceId(toLongOrNull(entry.getWorkspace() == null ? null : entry.getWorkspace().getId()))
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }

    static KnowledgeBaseVersionResponse toVersionResponse(KnowledgeBaseVersion version) {
        return KnowledgeBaseVersionResponse.builder()
                .id(version.getId())
                .knowledgeBaseEntryId(version.getKnowledgeBaseEntry().getId())
                .versionNo(version.getVersionNo())
                .sourceDocumentId(version.getSourceDocument() == null ? null : version.getSourceDocument().getId())
                .rawContent(version.getRawContent())
                .extractedContent(version.getExtractedContent())
                .status(version.getStatus())
                .ingestStatus(version.getIngestStatus())
                .visibility(version.getVisibility())
                .active(version.getActive())
                .ingestedAt(version.getIngestedAt())
                .ingestedById(version.getIngestedBy() == null ? null : version.getIngestedBy().getId())
                .errorMessage(version.getErrorMessage())
                .reviewDecision(version.getReviewDecision())
                .reviewedById(version.getReviewedBy() == null ? null : version.getReviewedBy().getId())
                .reviewedAt(version.getReviewedAt())
                .publishedById(version.getPublishedBy() == null ? null : version.getPublishedBy().getId())
                .publishedAt(version.getPublishedAt())
                .archivedById(version.getArchivedBy() == null ? null : version.getArchivedBy().getId())
                .archivedAt(version.getArchivedAt())
                .failedReason(version.getFailedReason())
                .createdAt(version.getCreatedAt())
                .updatedAt(version.getUpdatedAt())
                .build();
    }

    static KnowledgeIngestionJobResponse toJobResponse(KnowledgeIngestionJob job) {
        return KnowledgeIngestionJobResponse.builder()
                .id(job.getId())
                .knowledgeBaseVersionId(job.getKnowledgeBaseVersion().getId())
                .requestId(job.getRequestId())
                .status(job.getStatus())
                .jobPayload(job.getJobPayload())
                .errorMessage(job.getErrorMessage())
                .progressPercent(job.getProgressPercent())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .ingestedById(job.getIngestedBy() == null ? null : job.getIngestedBy().getId())
                .createdAt(job.getCreatedAt())
                .build();
    }

    private static Long toLongOrNull(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
