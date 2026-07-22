package com.analyzer.api.scheduler;

import com.analyzer.api.service.customerplan.CustomerPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionExpirationScheduler {

    private final CustomerPlanService customerPlanService;

    @Scheduled(fixedRate = 900_000)
    public void expireDuePlans() {
        customerPlanService.expireDuePlans();
    }
}
