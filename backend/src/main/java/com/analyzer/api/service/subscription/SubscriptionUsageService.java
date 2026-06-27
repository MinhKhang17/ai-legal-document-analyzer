package com.analyzer.api.service.subscription;

import com.analyzer.api.dto.subscription.SubscriptionUsageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubscriptionUsageService {

    Page<SubscriptionUsageResponse> getMyUsage(Long customerId, Pageable pageable);
}
