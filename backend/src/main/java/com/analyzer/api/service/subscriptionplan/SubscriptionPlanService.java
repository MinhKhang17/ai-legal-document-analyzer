package com.analyzer.api.service.subscriptionplan;

import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanRequest;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanResponse;
import com.analyzer.api.entity.SubscriptionPlan;
import java.util.List;

public interface SubscriptionPlanService {
    SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request);
    List<SubscriptionPlanResponse> getActivePlans();
    SubscriptionPlanResponse getPlanById(Long id);
    SubscriptionPlanResponse updatePlan(Long id, SubscriptionPlanRequest request);
    void deletePlan(Long id);

    SubscriptionPlanResponse toResponse(SubscriptionPlan plan);
}
