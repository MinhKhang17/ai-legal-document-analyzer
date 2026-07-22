package com.analyzer.api.service;

import com.analyzer.api.dto.subscription.SubscriptionQuotaUsageSummaryResponse;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;

public interface SubscriptionQuotaService {

    SubscriptionPlan getCurrentPlan(User user);

    SubscriptionQuotaUsageSummaryResponse getCurrentUsage(User user);

    void checkCanCreateWorkspace(User user);

    void checkCanUploadOrAnalyzeContract(User user, String workspaceId);

    void checkCanUploadOrAnalyzeContract(User user, String workspaceId, long fileSizeBytes);

    void checkCanAttachDocument(User user, String chatSessionId);

    void checkCanUseAiChat(User user, int estimatedInputTokens);

    void reserveAiChatQuota(User user, String requestId, int estimatedTokens);

    void attachAiQueryContext(User user, String requestId, String workspaceId, String chatSessionId,
                              String contextSnapshotJson);

    void completeAiChatQuota(User user, String requestId, int inputTokens, int outputTokens);

    void failAiChatQuota(User user, String requestId, String errorCode);

    void recordAiChatUsage(User user, int inputTokens, int outputTokens);

    void checkCanDraftContract(User user);

    void recordDraftContractUsage(User user);

    void checkCanCreateExpertTicket(User user);

    void checkCanCreateSupportTicket(User user);

    void recordExpertTicketUsage(User user);
}
