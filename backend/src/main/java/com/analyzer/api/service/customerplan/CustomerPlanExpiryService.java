package com.analyzer.api.service.customerplan;

import com.analyzer.api.entity.CustomerPlan;

public interface CustomerPlanExpiryService {

    CustomerPlan getActiveOrHandleExpiry(Long customerId);

    void expireDuePlans();
}
