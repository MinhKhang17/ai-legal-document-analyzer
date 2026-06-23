package com.analyzer.api.service;

import com.analyzer.api.dto.customerplan.CustomerPlanResponseDTO;
import com.analyzer.api.dto.customerplan.SubscribeRequestDTO;

public interface CustomerPlanService {
    CustomerPlanResponseDTO subscribe(Long customerId, SubscribeRequestDTO request);
    CustomerPlanResponseDTO getMyPlan(Long customerId);
    CustomerPlanResponseDTO cancelPlan(Long customerId, Long customerPlanId);
    void validateChatQuota(Long customerId);
    void recordChatUsage(Long customerId);
}
