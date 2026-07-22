package com.analyzer.api.service.customerplan.impl;

import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.SubscriptionPlan;
import org.springframework.stereotype.Component;

@Component
public class CustomerPlanSnapshotHelper {

    public void applySnapshot(CustomerPlan customerPlan, SubscriptionPlan plan) {
        customerPlan.setPlanNameSnapshot(plan.getPlanName());
        customerPlan.setPlanTypeSnapshot(plan.getPlanType());
        customerPlan.setPriceSnapshot(plan.getPrice());
        customerPlan.setBillingCycleDaysSnapshot(plan.getDurationDays());
        customerPlan.setAnalysisLimitSnapshot(plan.getMaxQuota());
        customerPlan.setAiTokenLimitSnapshot(plan.getAiQuota());
        customerPlan.setWorkspaceLimitSnapshot(plan.getMaxWorkspaces());
        customerPlan.setDocumentsPerWorkspaceLimitSnapshot(plan.getMaxContractsPerWorkspace());
        customerPlan.setStorageLimitMbSnapshot(plan.getStorageLimitMb());
        customerPlan.setMaxFileSizeMbSnapshot(plan.getMaxFileSizeMb());
        customerPlan.setAttachedDocumentsLimitSnapshot(plan.getMaxAttachedDocumentsPerSession());
        customerPlan.setDraftContractLimitSnapshot(plan.getMaxDraftContracts());
        customerPlan.setExpertTicketLimitSnapshot(plan.getTicketQuota());
        customerPlan.setAllowContactExpertTicketSnapshot(plan.getAllowContactExpertTicket());
    }

    public SubscriptionPlan effectivePlanView(CustomerPlan customerPlan) {
        SubscriptionPlan live = customerPlan.getSubscriptionPlan();
        if (customerPlan.getAnalysisLimitSnapshot() == null) {
            return live;
        }

        var view = SubscriptionPlan.builder()
                .planName(customerPlan.getPlanNameSnapshot())
                .planType(customerPlan.getPlanTypeSnapshot())
                .price(customerPlan.getPriceSnapshot())
                .durationDays(customerPlan.getBillingCycleDaysSnapshot())
                .maxQuota(customerPlan.getAnalysisLimitSnapshot())
                .aiQuota(customerPlan.getAiTokenLimitSnapshot())
                .maxWorkspaces(customerPlan.getWorkspaceLimitSnapshot())
                .maxContractsPerWorkspace(customerPlan.getDocumentsPerWorkspaceLimitSnapshot())
                .storageLimitMb(customerPlan.getStorageLimitMbSnapshot())
                .maxFileSizeMb(customerPlan.getMaxFileSizeMbSnapshot())
                .maxAttachedDocumentsPerSession(customerPlan.getAttachedDocumentsLimitSnapshot())
                .maxDraftContracts(customerPlan.getDraftContractLimitSnapshot())
                .ticketQuota(customerPlan.getExpertTicketLimitSnapshot())
                .allowContactExpertTicket(customerPlan.getAllowContactExpertTicketSnapshot());

        if (live != null) {
            view.id(live.getId())
                    .tier(live.getTier())
                    .description(live.getDescription())
                    .active(live.getActive())
                    .allowSystemErrorTicket(live.getAllowSystemErrorTicket())
                    .allowQueryErrorTicket(live.getAllowQueryErrorTicket())
                    .featureLimitsJson(live.getFeatureLimitsJson());
        }
        return view.build();
    }
}
