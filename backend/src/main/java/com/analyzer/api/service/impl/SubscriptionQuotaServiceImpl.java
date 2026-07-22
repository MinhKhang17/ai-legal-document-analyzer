package com.analyzer.api.service.impl;

import com.analyzer.api.dto.subscription.SubscriptionQuotaUsageSummaryResponse;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.SubscriptionUsage;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.AiQueryExecution;
import com.analyzer.api.enums.AiQueryExecutionStatus;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.enums.ContractGenerationStatus;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
import com.analyzer.api.enums.UsageEventType;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.ChatMessageRepository;
import com.analyzer.api.repository.AiQueryExecutionRepository;
import com.analyzer.api.repository.ChatSessionDocumentRepository;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.SubscriptionPlanRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.repository.contract.ContractGenerationJobRepository;
import com.analyzer.api.repository.subscription.SubscriptionUsageRepository;
import com.analyzer.api.service.SubscriptionQuotaService;
import com.analyzer.api.service.support.CustomerPlanExpiryHelper;
import com.analyzer.api.service.support.CustomerPlanSnapshotHelper;
import com.analyzer.api.service.support.UserQuotaLock;
import com.analyzer.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionQuotaServiceImpl implements SubscriptionQuotaService {

    private static final String ACTIVE_WORKSPACE_STATUS = "ACTIVE";
    private static final String DELETED_DOCUMENT_STATUS = "DELETED";
    private static final String READY_DOCUMENT_STATUS = "READY";
    private static final String USER_DOCUMENT_SOURCE_TYPE = "USER_DOCUMENT";
    private static final String SYSTEM_SANDBOX_NAME = "Contract Assistant Sandbox";
    private static final String SYSTEM_SANDBOX_DESCRIPTION = "System workspace for general contract assistant chat";
    private static final int FREE_SUPPORT_TICKET_LIMIT = 3;
    private static final String QUOTA_UPGRADE_MESSAGE =
            "The current plan quota is exhausted. Please purchase or upgrade your service plan.";

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final WorkspaceRepository workspaceRepository;
    private final DocumentRepository documentRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ContractGenerationJobRepository contractGenerationJobRepository;
    private final LegalTicketRepository legalTicketRepository;
    private final SubscriptionUsageRepository subscriptionUsageRepository;
    private final ChatSessionDocumentRepository chatSessionDocumentRepository;
    private final AiQueryExecutionRepository aiQueryExecutionRepository;
    private final CustomerPlanExpiryHelper customerPlanExpiryHelper;
    private final CustomerPlanSnapshotHelper customerPlanSnapshotHelper;
    private final UserQuotaLock userQuotaLock;

    @Override
    @Transactional
    public SubscriptionPlan getCurrentPlan(User user) {
        CustomerPlan activePlan = getActiveCustomerPlan(user.getId());
        if (activePlan != null && activePlan.getSubscriptionPlan() != null) {
            return customerPlanSnapshotHelper.effectivePlanView(activePlan);
        }

        SubscriptionPlan freePlan = subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")
                .orElseThrow(() -> new ConflictException(
                        "SUBSCRIPTION_NOT_FOUND", "The default Free subscription is not configured"));
        if (!Boolean.TRUE.equals(freePlan.getActive())) {
            throw new ConflictException("SUBSCRIPTION_INACTIVE", "The effective subscription is inactive");
        }
        return freePlan;
    }

    @Override
    @Transactional
    public SubscriptionQuotaUsageSummaryResponse getCurrentUsage(User user) {
        CustomerPlan activePlan = getActiveCustomerPlan(user.getId());
        SubscriptionPlan plan;
        if (activePlan != null && activePlan.getSubscriptionPlan() != null) {
            plan = customerPlanSnapshotHelper.effectivePlanView(activePlan);
        } else {
            plan = subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")
                    .orElseThrow(() -> new ConflictException(
                            "SUBSCRIPTION_NOT_FOUND", "The default Free subscription is not configured"));
            if (!Boolean.TRUE.equals(plan.getActive())) {
                throw new ConflictException("SUBSCRIPTION_INACTIVE", "The effective subscription is inactive");
            }
        }

        LocalDateTime periodStart = activePlan != null && activePlan.getBillingCycleStartAt() != null
                ? activePlan.getBillingCycleStartAt()
                : AppClock.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime periodEnd = activePlan != null && activePlan.getBillingCycleEndAt() != null
                ? activePlan.getBillingCycleEndAt()
                : periodStart.plusMonths(1);

        int contractAnalysisUsed = Math.toIntExact(documentRepository.countByUserIdAndSourceTypeAndStatusAndProcessedAtBetween(
                user.getId(), USER_DOCUMENT_SOURCE_TYPE, READY_DOCUMENT_STATUS, periodStart, periodEnd));
        long aiTokensUsed = chatMessageRepository.sumTotalTokensByUserAndRoleAndStatusBetween(
                user.getId(), ChatMessageRole.ASSISTANT, ChatMessageStatus.COMPLETED, periodStart, periodEnd);
        int draftContractsUsed = Math.toIntExact(contractGenerationJobRepository.countByRequesterIdAndStatusAndCreatedAtBetween(
                user.getId(), ContractGenerationStatus.COMPLETED, periodStart, periodEnd));
        int expertTicketsUsed = Math.toIntExact(
                legalTicketRepository.countByCreatedByIdAndTicketTypeAndDeletedFalseAndStatusNotInAndCreatedAtBetween(
                        user.getId(),
                        LegalTicketType.CONTACT_EXPERT,
                        List.of(LegalTicketStatus.CANCELLED, LegalTicketStatus.REJECTED_BY_ADMIN),
                        periodStart,
                        periodEnd));
        int workspacesUsed = Math.toIntExact(workspaceRepository.countQuotaWorkspaces(
                user.getId(), ACTIVE_WORKSPACE_STATUS, SYSTEM_SANDBOX_NAME, SYSTEM_SANDBOX_DESCRIPTION));
        int documentsUsed = Math.toIntExact(documentRepository.countByUserIdAndStatusNot(user.getId(), DELETED_DOCUMENT_STATUS));
        long storageUsedBytes = documentRepository.sumFileSizeByUserIdAndStatusNot(user.getId(), DELETED_DOCUMENT_STATUS);

        return SubscriptionQuotaUsageSummaryResponse.builder()
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .contractAnalysisUsed(contractAnalysisUsed)
                .contractAnalysisLimit(plan.getMaxQuota())
                .aiTokensUsed(aiTokensUsed)
                .aiTokensLimit(plan.getAiQuota())
                .draftContractsUsed(draftContractsUsed)
                .draftContractsLimit(plan.getMaxDraftContracts())
                .expertTicketsUsed(expertTicketsUsed)
                .expertTicketsLimit(plan.getTicketQuota())
                .workspacesUsed(workspacesUsed)
                .workspacesLimit(plan.getMaxWorkspaces())
                .documentsUsed(documentsUsed)
                .documentsLimit(plan.getMaxWorkspaces() == null || plan.getMaxContractsPerWorkspace() == null
                        ? null : plan.getMaxWorkspaces() * plan.getMaxContractsPerWorkspace())
                .storageUsedBytes(storageUsedBytes)
                .storageLimitBytes(plan.getStorageLimitMb() == null ? null : plan.getStorageLimitMb() * 1024L * 1024L)
                .build();
    }

    @Override
    @Transactional
    public void checkCanCreateWorkspace(User user) {
        userQuotaLock.acquire(user.getId());
        SubscriptionPlan plan = getCurrentPlan(user);
        long currentWorkspaceCount = workspaceRepository.countQuotaWorkspaces(
                user.getId(), ACTIVE_WORKSPACE_STATUS, SYSTEM_SANDBOX_NAME, SYSTEM_SANDBOX_DESCRIPTION);
        if (plan.getMaxWorkspaces() != null && currentWorkspaceCount >= plan.getMaxWorkspaces()) {
            throw new ConflictException("WORKSPACE_LIMIT_REACHED", QUOTA_UPGRADE_MESSAGE);
        }
    }

    @Override
    @Transactional
    public void checkCanUploadOrAnalyzeContract(User user, String workspaceId) {
        checkCanUploadOrAnalyzeContract(user, workspaceId, 0);
    }

    @Override
    @Transactional
    public void checkCanUploadOrAnalyzeContract(User user, String workspaceId, long fileSizeBytes) {
        userQuotaLock.acquire(user.getId());
        SubscriptionPlan plan = getCurrentPlan(user);
        SubscriptionQuotaUsageSummaryResponse usage = getCurrentUsage(user);

        if (plan.getMaxQuota() != null && usage.getContractAnalysisUsed() >= plan.getMaxQuota()) {
            throw new ConflictException("CONTRACT_ANALYSIS_QUOTA_EXCEEDED", QUOTA_UPGRADE_MESSAGE);
        }

        long workspaceContractCount = documentRepository.countByWorkspaceIdAndUserIdAndStatusNot(
                workspaceId, user.getId(), DELETED_DOCUMENT_STATUS);
        if (plan.getMaxContractsPerWorkspace() != null && workspaceContractCount >= plan.getMaxContractsPerWorkspace()) {
            throw new ConflictException("CONTRACTS_PER_WORKSPACE_LIMIT_EXCEEDED", QUOTA_UPGRADE_MESSAGE);
        }
        if (plan.getMaxFileSizeMb() != null && fileSizeBytes > plan.getMaxFileSizeMb() * 1024L * 1024L) {
            throw new ConflictException("MAX_FILE_SIZE_EXCEEDED", QUOTA_UPGRADE_MESSAGE);
        }
        long storageUsed = documentRepository.sumFileSizeByUserIdAndStatusNot(user.getId(), DELETED_DOCUMENT_STATUS);
        if (plan.getStorageLimitMb() != null
                && storageUsed + Math.max(fileSizeBytes, 0) > plan.getStorageLimitMb() * 1024L * 1024L) {
            throw new ConflictException("STORAGE_LIMIT_EXCEEDED", QUOTA_UPGRADE_MESSAGE);
        }
    }

    @Override
    @Transactional
    public void checkCanAttachDocument(User user, String chatSessionId) {
        userQuotaLock.acquire(user.getId());
        SubscriptionPlan plan = getCurrentPlan(user);
        if (plan.getMaxAttachedDocumentsPerSession() == null) {
            return;
        }
        // Count fresh, after acquiring the lock — a count taken by the caller before the lock
        // would let concurrent attach-different-document requests all read the same stale
        // count and all pass, bypassing the limit.
        long currentlyAttached = chatSessionDocumentRepository.countByChatSessionIdAndUserIdAndActiveTrue(
                chatSessionId, user.getId());
        if (currentlyAttached >= plan.getMaxAttachedDocumentsPerSession()) {
            throw new ConflictException("ATTACHED_DOCUMENT_LIMIT_EXCEEDED", QUOTA_UPGRADE_MESSAGE);
        }
    }

    // REQUIRES_NEW: caller holds this transaction open across the (slow) AI call that follows
    // the check. Running the lock+check in its own short transaction releases the advisory
    // lock and DB connection immediately instead of pinning them for the AI call's duration.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkCanUseAiChat(User user, int estimatedInputTokens) {
        userQuotaLock.acquire(user.getId());
        SubscriptionPlan plan = getCurrentPlan(user);
        if (plan.getAiQuota() == null) {
            return;
        }

        SubscriptionQuotaUsageSummaryResponse usage = getCurrentUsage(user);
        if (usage.getAiTokensUsed() + Math.max(estimatedInputTokens, 0L) > plan.getAiQuota()) {
            throw new ConflictException("AI_TOKEN_QUOTA_EXCEEDED", QUOTA_UPGRADE_MESSAGE);
        }
    }

    @Override
    @Transactional
    public void recordAiChatUsage(User user, int inputTokens, int outputTokens) {
        recordUsage(user, UsageEventType.AI_QUERY, inputTokens + outputTokens, "ai-chat");
    }

    // REQUIRES_NEW: same reasoning as checkCanUseAiChat — contract drafting calls the AI
    // service synchronously right after this check, within the caller's transaction.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkCanDraftContract(User user) {
        userQuotaLock.acquire(user.getId());
        SubscriptionPlan plan = getCurrentPlan(user);
        if (plan.getMaxDraftContracts() == null) {
            return;
        }

        SubscriptionQuotaUsageSummaryResponse usage = getCurrentUsage(user);
        if (usage.getDraftContractsUsed() >= plan.getMaxDraftContracts()) {
            throw new ConflictException("DRAFT_CONTRACT_QUOTA_EXCEEDED", QUOTA_UPGRADE_MESSAGE);
        }
    }

    @Override
    @Transactional
    public void recordDraftContractUsage(User user) {
        recordUsage(user, UsageEventType.CONTRACT_GENERATION, 1, "contract-draft");
    }

    @Override
    @Transactional
    public void checkCanCreateExpertTicket(User user) {
        userQuotaLock.acquire(user.getId());
        SubscriptionPlan plan = getCurrentPlan(user);
        if (!"PREMIUM".equalsIgnoreCase(plan.getPlanType())
                || !Boolean.TRUE.equals(plan.getAllowContactExpertTicket())) {
            throw new com.analyzer.api.exception.common.ForbiddenException(
                    "EXPERT_TICKET_REQUIRES_PREMIUM", "An upgraded plan is required to contact an expert");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveAiChatQuota(User user, String requestId, int estimatedTokens) {
        userQuotaLock.acquire(user.getId());
        AiQueryExecution existing = aiQueryExecutionRepository
                .findByRequestIdAndUserId(requestId, user.getId())
                .orElse(null);
        if (existing != null && existing.getStatus() == AiQueryExecutionStatus.PROCESSING) {
            throw new ConflictException("QUERY_ALREADY_PROCESSING", "This query is already processing");
        }
        if (existing != null && existing.getStatus() == AiQueryExecutionStatus.COMPLETED) {
            return;
        }

        SubscriptionPlan plan = getCurrentPlan(user);
        CustomerPlan activePlan = getActiveCustomerPlan(user.getId());
        LocalDateTime periodStart = activePlan != null && activePlan.getBillingCycleStartAt() != null
                ? activePlan.getBillingCycleStartAt()
                : AppClock.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime periodEnd = activePlan != null && activePlan.getBillingCycleEndAt() != null
                ? activePlan.getBillingCycleEndAt()
                : periodStart.plusMonths(1);
        int normalizedEstimate = Math.max(estimatedTokens, 0);
        long completedUsage = getCurrentUsage(user).getAiTokensUsed();
        long activeReservations = aiQueryExecutionRepository.sumReservedTokens(
                user.getId(), AiQueryExecutionStatus.PROCESSING, periodStart, periodEnd, requestId);
        if (plan.getAiQuota() != null
                && completedUsage + activeReservations + normalizedEstimate > plan.getAiQuota()) {
            throw new ConflictException("TOKEN_QUOTA_EXCEEDED", QUOTA_UPGRADE_MESSAGE);
        }

        AiQueryExecution execution = existing != null ? existing : AiQueryExecution.builder()
                .requestId(requestId)
                .user(user)
                .build();
        execution.setStatus(AiQueryExecutionStatus.PROCESSING);
        execution.setEstimatedTokens(normalizedEstimate);
        execution.setActualInputTokens(null);
        execution.setActualOutputTokens(null);
        execution.setErrorCode(null);
        execution.setPlanTypeSnapshot(plan.getPlanType());
        aiQueryExecutionRepository.save(execution);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachAiQueryContext(User user, String requestId, String workspaceId, String chatSessionId,
                                     String contextSnapshotJson) {
        AiQueryExecution execution = aiQueryExecutionRepository.findByRequestIdAndUserId(requestId, user.getId())
                .orElseThrow(() -> new ConflictException("CONCURRENT_MODIFICATION", "Query reservation not found"));
        if (execution.getStatus() != AiQueryExecutionStatus.PROCESSING) {
            throw new ConflictException("CONCURRENT_MODIFICATION", "Query is no longer processing");
        }
        execution.setWorkspaceId(workspaceId);
        execution.setChatSessionId(chatSessionId);
        execution.setContextSnapshotJson(contextSnapshotJson);
        aiQueryExecutionRepository.save(execution);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeAiChatQuota(User user, String requestId, int inputTokens, int outputTokens) {
        AiQueryExecution execution = aiQueryExecutionRepository.findByRequestIdAndUserId(requestId, user.getId())
                .orElseThrow(() -> new ConflictException("CONCURRENT_MODIFICATION", "Query reservation not found"));
        if (execution.getStatus() == AiQueryExecutionStatus.COMPLETED) {
            return;
        }
        execution.setActualInputTokens(Math.max(inputTokens, 0));
        execution.setActualOutputTokens(Math.max(outputTokens, 0));
        execution.setStatus(AiQueryExecutionStatus.COMPLETED);
        aiQueryExecutionRepository.save(execution);
        recordUsage(user, UsageEventType.AI_QUERY,
                Math.max(inputTokens, 0) + Math.max(outputTokens, 0), requestId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failAiChatQuota(User user, String requestId, String errorCode) {
        aiQueryExecutionRepository.findByRequestIdAndUserId(requestId, user.getId()).ifPresent(execution -> {
            if (execution.getStatus() != AiQueryExecutionStatus.COMPLETED) {
                execution.setStatus(AiQueryExecutionStatus.FAILED);
                execution.setErrorCode(errorCode);
                aiQueryExecutionRepository.save(execution);
            }
        });
    }

    @Override
    @Transactional
    public void checkCanCreateSupportTicket(User user) {
        userQuotaLock.acquire(user.getId());
        SubscriptionPlan plan = getCurrentPlan(user);
        if (!"FREE".equalsIgnoreCase(plan.getPlanType())) {
            return;
        }

        CustomerPlan activePlan = getActiveCustomerPlan(user.getId());
        LocalDateTime periodStart = activePlan != null && activePlan.getBillingCycleStartAt() != null
                ? activePlan.getBillingCycleStartAt()
                : AppClock.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime periodEnd = activePlan != null && activePlan.getBillingCycleEndAt() != null
                ? activePlan.getBillingCycleEndAt()
                : periodStart.plusMonths(1);
        long supportTicketsUsed = legalTicketRepository
                .countByCreatedByIdAndTicketTypeInAndDeletedFalseAndStatusNotInAndCreatedAtBetween(
                        user.getId(),
                        List.of(LegalTicketType.SYSTEM_ERROR, LegalTicketType.QUERY_ERROR),
                        List.of(LegalTicketStatus.CANCELLED, LegalTicketStatus.REJECTED_BY_ADMIN),
                        periodStart,
                        periodEnd);
        if (supportTicketsUsed >= FREE_SUPPORT_TICKET_LIMIT) {
            throw new ConflictException(
                    "FREE_SUPPORT_TICKET_LIMIT_REACHED",
                    QUOTA_UPGRADE_MESSAGE);
        }
    }

    @Override
    @Transactional
    public void recordExpertTicketUsage(User user) {
        recordUsage(user, UsageEventType.TICKET_CREATE, 1, "expert-ticket");
    }

    private CustomerPlan getActiveCustomerPlan(Long userId) {
        CustomerPlan activePlan = customerPlanExpiryHelper.getActiveOrHandleExpiry(userId);
        if (activePlan != null && (activePlan.getSubscriptionPlan() == null
                || !Boolean.TRUE.equals(activePlan.getSubscriptionPlan().getActive()))) {
            throw new ConflictException("SUBSCRIPTION_INACTIVE", "The active subscription record references an inactive plan");
        }
        return activePlan;
    }

    private void recordUsage(User user, UsageEventType usageType, int consumedUnits, String referenceId) {
        CustomerPlan activePlan = getActiveCustomerPlan(user.getId());
        if (activePlan == null || activePlan.getSubscriptionPlan() == null) {
            return;
        }

        subscriptionUsageRepository.save(SubscriptionUsage.builder()
                .customerPlan(activePlan)
                .usageType(usageType)
                .referenceId(referenceId)
                .consumedUnits(Math.max(consumedUnits, 0))
                .build());
    }
}
