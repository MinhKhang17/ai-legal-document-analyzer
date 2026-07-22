package com.analyzer.api.repository;

import com.analyzer.api.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    List<SubscriptionPlan> findByActiveTrue();
    boolean existsByPlanName(String planName);
    boolean existsByPlanNameIgnoreCase(String planName);
    boolean existsByPlanTypeIgnoreCase(String planType);
    Optional<SubscriptionPlan> findByPlanTypeIgnoreCase(String planType);
}
