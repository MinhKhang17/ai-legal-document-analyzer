package com.analyzer.api.service.impl;
import com.analyzer.api.service.subscription.impl.SubscriptionQuotaServiceImpl;

import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.chatmessage.ChatMessageRepository;
import com.analyzer.api.repository.ai.AiQueryExecutionRepository;
import com.analyzer.api.repository.chatsession.ChatSessionDocumentRepository;
import com.analyzer.api.repository.document.DocumentRepository;
import com.analyzer.api.repository.legalticket.LegalTicketRepository;
import com.analyzer.api.repository.subscriptionplan.SubscriptionPlanRepository;
import com.analyzer.api.repository.workspace.WorkspaceRepository;
import com.analyzer.api.repository.contract.ContractGenerationJobRepository;
import com.analyzer.api.repository.subscription.SubscriptionUsageRepository;
import com.analyzer.api.service.customerplan.CustomerPlanExpiryService;
import com.analyzer.api.service.customerplan.impl.CustomerPlanSnapshotHelper;
import com.analyzer.api.util.UserQuotaLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionQuotaServiceImplTest {

    private static final String SANDBOX_DESCRIPTION = "System workspace for general contract assistant chat";
    private static final String SANDBOX_NAME = "Contract Assistant Sandbox";

    @Mock SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock DocumentRepository documentRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock AiQueryExecutionRepository aiQueryExecutionRepository;
    @Mock ContractGenerationJobRepository contractGenerationJobRepository;
    @Mock LegalTicketRepository legalTicketRepository;
    @Mock SubscriptionUsageRepository subscriptionUsageRepository;
    @Mock ChatSessionDocumentRepository chatSessionDocumentRepository;
    @Mock CustomerPlanExpiryService customerPlanExpiryHelper;
    @Mock CustomerPlanSnapshotHelper customerPlanSnapshotHelper;
    @Mock UserQuotaLock userQuotaLock;
    @InjectMocks SubscriptionQuotaServiceImpl service;

    @Test
    void freeUserCreatesWorkspaceWithinQuota() {
        User user = User.builder().id(10L).build();
        SubscriptionPlan free = plan("FREE", 1, true);
        when(customerPlanExpiryHelper.getActiveOrHandleExpiry(10L)).thenReturn(null);
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")).thenReturn(Optional.of(free));
        when(workspaceRepository.countQuotaWorkspaces(10L, "ACTIVE", SANDBOX_NAME, SANDBOX_DESCRIPTION)).thenReturn(0L);

        assertThatCode(() -> service.checkCanCreateWorkspace(user)).doesNotThrowAnyException();
    }

    @Test
    void userAtWorkspaceQuotaGetsExplicitConflict() {
        User user = User.builder().id(10L).build();
        when(customerPlanExpiryHelper.getActiveOrHandleExpiry(10L)).thenReturn(null);
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE"))
                .thenReturn(Optional.of(plan("FREE", 1, true)));
        when(workspaceRepository.countQuotaWorkspaces(10L, "ACTIVE", SANDBOX_NAME, SANDBOX_DESCRIPTION)).thenReturn(1L);

        assertThatThrownBy(() -> service.checkCanCreateWorkspace(user))
                .isInstanceOf(ConflictException.class)
                .extracting(error -> ((ConflictException) error).getErrorCode())
                .isEqualTo("WORKSPACE_LIMIT_REACHED");
    }

    @Test
    void paidUserCreatesWorkspaceWithinQuota() {
        User user = User.builder().id(20L).build();
        SubscriptionPlan paid = plan("PREMIUM", 20, true);
        CustomerPlan active = CustomerPlan.builder()
                .customer(user).subscriptionPlan(paid).status(PlanStatus.ACTIVE).build();
        when(customerPlanExpiryHelper.getActiveOrHandleExpiry(20L)).thenReturn(active);
        when(customerPlanSnapshotHelper.effectivePlanView(active)).thenReturn(paid);
        when(workspaceRepository.countQuotaWorkspaces(20L, "ACTIVE", SANDBOX_NAME, SANDBOX_DESCRIPTION)).thenReturn(19L);

        assertThatCode(() -> service.checkCanCreateWorkspace(user)).doesNotThrowAnyException();
    }

    @Test
    void activeSubscriptionReferencingInactivePlanGetsExplicitConflict() {
        User user = User.builder().id(30L).build();
        CustomerPlan active = CustomerPlan.builder()
                .customer(user).subscriptionPlan(plan("PREMIUM", 20, false)).status(PlanStatus.ACTIVE).build();
        when(customerPlanExpiryHelper.getActiveOrHandleExpiry(30L)).thenReturn(active);

        assertThatThrownBy(() -> service.checkCanCreateWorkspace(user))
                .isInstanceOf(ConflictException.class)
                .extracting(error -> ((ConflictException) error).getErrorCode())
                .isEqualTo("SUBSCRIPTION_INACTIVE");
    }

    @Test
    void freeUserCreatesSystemOrQueryTicketWithinThreeTicketLimit() {
        User user = User.builder().id(40L).build();
        when(customerPlanExpiryHelper.getActiveOrHandleExpiry(40L)).thenReturn(null);
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE"))
                .thenReturn(Optional.of(plan("FREE", 1, true)));
        when(legalTicketRepository
                .countByCreatedByIdAndTicketTypeInAndDeletedFalseAndStatusNotInAndCreatedAtBetween(
                        any(), anyList(), anyList(), any(), any()))
                .thenReturn(2L);

        assertThatCode(() -> service.checkCanCreateSupportTicket(user)).doesNotThrowAnyException();
    }

    @Test
    void freeUserAtSystemAndQueryTicketLimitGetsExplicitConflict() {
        User user = User.builder().id(41L).build();
        when(customerPlanExpiryHelper.getActiveOrHandleExpiry(41L)).thenReturn(null);
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE"))
                .thenReturn(Optional.of(plan("FREE", 1, true)));
        when(legalTicketRepository
                .countByCreatedByIdAndTicketTypeInAndDeletedFalseAndStatusNotInAndCreatedAtBetween(
                        any(), anyList(), anyList(), any(), any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.checkCanCreateSupportTicket(user))
                .isInstanceOf(ConflictException.class)
                .extracting(error -> ((ConflictException) error).getErrorCode())
                .isEqualTo("FREE_SUPPORT_TICKET_LIMIT_REACHED");
    }

    private SubscriptionPlan plan(String type, int maxWorkspaces, boolean active) {
        return SubscriptionPlan.builder()
                .planType(type)
                .maxWorkspaces(maxWorkspaces)
                .active(active)
                .build();
    }
}
