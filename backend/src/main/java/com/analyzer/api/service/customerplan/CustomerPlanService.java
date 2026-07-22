package com.analyzer.api.service.customerplan;

import com.analyzer.api.dto.customerplan.CustomerPlanResponse;
import com.analyzer.api.dto.customerplan.SubscribeRequest;

public interface CustomerPlanService {
    CustomerPlanResponse subscribe(Long customerId, SubscribeRequest request);

    CustomerPlanResponse getMyPlan(Long customerId);

    CustomerPlanResponse cancelPlan(Long customerId, Long customerPlanId);

    CustomerPlanResponse cancelPlanAndActivateFree(Long customerId, Long customerPlanId, String reason);

    void expireDuePlans();
}