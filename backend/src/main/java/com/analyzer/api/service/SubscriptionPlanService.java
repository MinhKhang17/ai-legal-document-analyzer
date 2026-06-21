package com.analyzer.api.service;

import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanRequestDTO;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanResponseDTO;
import java.util.List;

public interface SubscriptionPlanService {
    SubscriptionPlanResponseDTO createPlan(SubscriptionPlanRequestDTO request);
    List<SubscriptionPlanResponseDTO> getActivePlans();
    SubscriptionPlanResponseDTO getPlanById(Long id);
    SubscriptionPlanResponseDTO updatePlan(Long id, SubscriptionPlanRequestDTO request);
    void deletePlan(Long id);
}
