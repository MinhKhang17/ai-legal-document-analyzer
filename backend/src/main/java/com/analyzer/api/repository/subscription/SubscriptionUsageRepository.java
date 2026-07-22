package com.analyzer.api.repository.subscription;

import com.analyzer.api.entity.SubscriptionUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionUsageRepository extends JpaRepository<SubscriptionUsage, Long> {

    List<SubscriptionUsage> findByCustomerPlanId(Long customerPlanId);

    Page<SubscriptionUsage> findByCustomerPlanCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
}
