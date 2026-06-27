package com.analyzer.api.service.subscription.impl;

import com.analyzer.api.dto.subscription.SubscriptionUsageResponse;
import com.analyzer.api.service.subscription.SubscriptionUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class SubscriptionUsageServiceImpl implements SubscriptionUsageService {

    @Override
    public Page<SubscriptionUsageResponse> getMyUsage(Long customerId, Pageable pageable) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }
}
