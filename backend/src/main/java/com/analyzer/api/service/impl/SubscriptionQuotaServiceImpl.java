package com.analyzer.api.service.impl;

import com.analyzer.api.dto.subscription.SubscriptionQuotaUsageSummaryResponse;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.SubscriptionUsage;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.enums.ContractGenerationStatus;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.UsageEventType;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.ChatMessageRepository;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.SubscriptionPlanRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.repository.contract.ContractGenerationJobRepository;
import com.analyzer.api.repository.subscription.SubscriptionUsageRepository;
import com.analyzer.api.service.SubscriptionQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionQuotaServiceImpl implements SubscriptionQuotaService {

    private static final String ACTIVE_WORKSPACE_STATUS = "ACTIVE";
    private static final String DELETED_DOCUMENT_STATUS = "DELETED";
    private static final String USER_DOCUMENT_SOURCE_TYPE = "USER_DOCUMENT";

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CustomerPlanRepository customerPlanRepository;
    private final WorkspaceRepository workspaceRepository;
    private final DocumentRepository documentRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ContractGenerationJobRepository contractGenerationJobRepository;
    private final LegalTicketRepository legalTicketRepository;
    private final SubscriptionUsageRepository subscriptionUsageRepository;

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlan getCurrentPlan(User user) {
        CustomerPlan activePlan = getActiveCustomerPlan(user.getId());
        if (activePlan != null && activePlan.getSubscriptionPlan() != null) {
            return activePlan.getSubscriptionPlan();
        }

        return subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")
                .orElseThrow(() -> new ResourceNotFoundException("FREE_PLAN_NOT_CONFIGURED"));
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionQuotaUsageSummaryResponse getCurrentUsage(User user) {
        LocalDateTime periodStart = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime periodEnd = periodStart.plusMonths(1);
        SubscriptionPlan plan = getCurrentPlan(user);

        int contractAnalysisUsed = Math.toIntExact(documentRepository.countByUserIdAndSourceTypeAndUploadedAtBetween(
                user.getId(), USER_DOCUMENT_SOURCE_TYPE, periodStart, periodEnd));
        long aiTokensUsed = chatMessageRepository.sumTotalTokensByUserAndRoleAndStatusBetween(
                user.getId(), ChatMessageRole.ASSISTANT, ChatMessageStatus.COMPLETED, periodStart, periodEnd);
        int draftContractsUsed = Math.toIntExact(contractGenerationJobRepository.countByRequesterIdAndStatusAndCreatedAtBetween(
                user.getId(), ContractGenerationStatus.COMPLETED, periodStart, periodEnd));
        int expertTicketsUsed = Math.toIntExact(
                legalTicketRepository.countByCreatedByIdAndDeletedFalseAndStatusNotInAndCreatedAtBetween(
                        user.getId(),
                        List.of(LegalTicketStatus.CANCELLED, LegalTicketStatus.REJECTED_BY_ADMIN),
                        periodStart,
                        periodEnd));
        int workspacesUsed = Math.toIntExact(workspaceRepository.countByUserIdAndStatus(user.getId(), ACTIVE_WORKSPACE_STATUS));

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
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCanCreateWorkspace(User user) {
        SubscriptionPlan plan = getCurrentPlan(user);
        long currentWorkspaceCount = workspaceRepository.countByUserIdAndStatus(user.getId(), ACTIVE_WORKSPACE_STATUS);
        if (plan.getMaxWorkspaces() != null && currentWorkspaceCount >= plan.getMaxWorkspaces()) {
            throw new ConflictException("WORKSPACE_LIMIT_EXCEEDED");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCanUploadOrAnalyzeContract(User user, String workspaceId) {
        SubscriptionPlan plan = getCurrentPlan(user);
        SubscriptionQuotaUsageSummaryResponse usage = getCurrentUsage(user);

        if (plan.getMaxQuota() != null && usage.getContractAnalysisUsed() >= plan.getMaxQuota()) {
            throw new ConflictException("CONTRACT_ANALYSIS_QUOTA_EXCEEDED");
        }

        long workspaceContractCount = documentRepository.countByWorkspaceIdAndUserIdAndStatusNot(
                workspaceId, user.getId(), DELETED_DOCUMENT_STATUS);
        if (plan.getMaxContractsPerWorkspace() != null && workspaceContractCount >= plan.getMaxContractsPerWorkspace()) {
            throw new ConflictException("CONTRACTS_PER_WORKSPACE_LIMIT_EXCEEDED");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCanUseAiChat(User user, int estimatedInputTokens) {
        SubscriptionPlan plan = getCurrentPlan(user);
        if (plan.getAiQuota() == null) {
            return;
        }

        SubscriptionQuotaUsageSummaryResponse usage = getCurrentUsage(user);
        if (usage.getAiTokensUsed() + Math.max(estimatedInputTokens, 0L) > plan.getAiQuota()) {
            throw new ConflictException("AI_TOKEN_QUOTA_EXCEEDED");
        }
    }

    @Override
    @Transactional
    public void recordAiChatUsage(User user, int inputTokens, int outputTokens) {
        recordUsage(user, UsageEventType.AI_QUERY, inputTokens + outputTokens, "ai-chat");
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCanDraftContract(User user) {
        SubscriptionPlan plan = getCurrentPlan(user);
        if (plan.getMaxDraftContracts() == null) {
            return;
        }

        SubscriptionQuotaUsageSummaryResponse usage = getCurrentUsage(user);
        if (usage.getDraftContractsUsed() >= plan.getMaxDraftContracts()) {
            throw new ConflictException("DRAFT_CONTRACT_QUOTA_EXCEEDED");
        }
    }

    @Override
    @Transactional
    public void recordDraftContractUsage(User user) {
        recordUsage(user, UsageEventType.CONTRACT_GENERATION, 1, "contract-draft");
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCanCreateExpertTicket(User user) {
        SubscriptionPlan plan = getCurrentPlan(user);
        int ticketQuota = plan.getTicketQuota() == null ? 0 : plan.getTicketQuota();
        if (ticketQuota <= 0) {
            throw new ConflictException("EXPERT_TICKET_QUOTA_EXCEEDED");
        }

        SubscriptionQuotaUsageSummaryResponse usage = getCurrentUsage(user);
        if (usage.getExpertTicketsUsed() >= ticketQuota) {
            throw new ConflictException("EXPERT_TICKET_QUOTA_EXCEEDED");
        }
    }

    @Override
    @Transactional
    public void recordExpertTicketUsage(User user) {
        recordUsage(user, UsageEventType.TICKET_CREATE, 1, "expert-ticket");
    }

    private CustomerPlan getActiveCustomerPlan(Long userId) {
        CustomerPlan activePlan = customerPlanRepository.findTopByCustomerIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE)
                .orElse(null);
        if (activePlan != null && activePlan.getEndDate() != null && LocalDateTime.now().isAfter(activePlan.getEndDate())) {
            activePlan.setStatus(PlanStatus.EXPIRED);
            customerPlanRepository.save(activePlan);
            return null;
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
