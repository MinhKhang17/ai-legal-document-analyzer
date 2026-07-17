package com.analyzer.api.service.subscription.impl;

import com.analyzer.api.dto.subscription.SubscriptionUsageResponse;
import com.analyzer.api.entity.SubscriptionUsage;
import com.analyzer.api.repository.subscription.SubscriptionUsageRepository;
import com.analyzer.api.service.subscription.SubscriptionUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionUsageServiceImpl implements SubscriptionUsageService {

    private final SubscriptionUsageRepository subscriptionUsageRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionUsageResponse> getMyUsage(Long customerId, Pageable pageable) {
        return subscriptionUsageRepository.findByCustomerPlanCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(this::toResponse);
    }

    private SubscriptionUsageResponse toResponse(SubscriptionUsage usage) {
        return SubscriptionUsageResponse.builder()
                .id(usage.getId())
                .customerPlanId(usage.getCustomerPlan().getId())
                .usageType(usage.getUsageType())
                .referenceId(usage.getReferenceId())
                .consumedUnits(usage.getConsumedUnits())
                .metadataJson(usage.getMetadataJson())
                .createdAt(usage.getCreatedAt())
                .build();
    }
}
