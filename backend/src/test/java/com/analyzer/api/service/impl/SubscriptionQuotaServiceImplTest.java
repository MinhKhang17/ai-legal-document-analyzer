package com.analyzer.api.service.impl;

import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.ChatMessageRepository;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.SubscriptionPlanRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.repository.contract.ContractGenerationJobRepository;
import com.analyzer.api.repository.subscription.SubscriptionUsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionQuotaServiceImplTest {

    private static final String SANDBOX_DESCRIPTION = "System workspace for general contract assistant chat";
    private static final String SANDBOX_NAME = "Contract Assistant Sandbox";

    @Mock SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock CustomerPlanRepository customerPlanRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock DocumentRepository documentRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock ContractGenerationJobRepository contractGenerationJobRepository;
    @Mock LegalTicketRepository legalTicketRepository;
    @Mock SubscriptionUsageRepository subscriptionUsageRepository;
    @InjectMocks SubscriptionQuotaServiceImpl service;

    @Test
    void freeUserCreatesWorkspaceWithinQuota() {
        User user = User.builder().id(10L).build();
        SubscriptionPlan free = plan("FREE", 1, true);
        when(customerPlanRepository.findTopByCustomerIdAndStatusOrderByCreatedAtDesc(10L, PlanStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")).thenReturn(Optional.of(free));
        when(workspaceRepository.countQuotaWorkspaces(10L, "ACTIVE", SANDBOX_NAME, SANDBOX_DESCRIPTION)).thenReturn(0L);

        assertThatCode(() -> service.checkCanCreateWorkspace(user)).doesNotThrowAnyException();
    }

    @Test
    void userAtWorkspaceQuotaGetsExplicitConflict() {
        User user = User.builder().id(10L).build();
        when(customerPlanRepository.findTopByCustomerIdAndStatusOrderByCreatedAtDesc(10L, PlanStatus.ACTIVE))
                .thenReturn(Optional.empty());
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
        when(customerPlanRepository.findTopByCustomerIdAndStatusOrderByCreatedAtDesc(20L, PlanStatus.ACTIVE))
                .thenReturn(Optional.of(active));
        when(workspaceRepository.countQuotaWorkspaces(20L, "ACTIVE", SANDBOX_NAME, SANDBOX_DESCRIPTION)).thenReturn(19L);

        assertThatCode(() -> service.checkCanCreateWorkspace(user)).doesNotThrowAnyException();
    }

    @Test
    void activeSubscriptionReferencingInactivePlanGetsExplicitConflict() {
        User user = User.builder().id(30L).build();
        CustomerPlan active = CustomerPlan.builder()
                .customer(user).subscriptionPlan(plan("PREMIUM", 20, false)).status(PlanStatus.ACTIVE).build();
        when(customerPlanRepository.findTopByCustomerIdAndStatusOrderByCreatedAtDesc(30L, PlanStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.checkCanCreateWorkspace(user))
                .isInstanceOf(ConflictException.class)
                .extracting(error -> ((ConflictException) error).getErrorCode())
                .isEqualTo("SUBSCRIPTION_INACTIVE");
    }

    private SubscriptionPlan plan(String type, int maxWorkspaces, boolean active) {
        return SubscriptionPlan.builder()
                .planType(type)
                .maxWorkspaces(maxWorkspaces)
                .active(active)
                .build();
    }
}
