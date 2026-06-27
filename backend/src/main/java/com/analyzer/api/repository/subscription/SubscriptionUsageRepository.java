package com.analyzer.api.repository.subscription;

import com.analyzer.api.entity.SubscriptionUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionUsageRepository extends JpaRepository<SubscriptionUsage, Long> {

    List<SubscriptionUsage> findByCustomerPlanId(Long customerPlanId);
}
