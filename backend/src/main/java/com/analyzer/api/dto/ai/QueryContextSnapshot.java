package com.analyzer.api.dto.ai;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record QueryContextSnapshot(
        String requestId,
        Long userId,
        String workspaceId,
        String chatSessionId,
        List<String> attachedDocumentIds,
        List<DocumentVersion> documentVersions,
        List<String> messageAttachedDocumentIds,
        String focusedDocumentId,
        String conversationSummary,
        List<RagQueryRequest.ConversationMessage> recentHistory,
        String planType,
        String termsVersion,
        String privacyPolicyVersion,
        Integer estimatedTokens,
        LocalDateTime createdAt) {
    public record DocumentVersion(String documentId, LocalDateTime updatedAt, String status) {}
}
