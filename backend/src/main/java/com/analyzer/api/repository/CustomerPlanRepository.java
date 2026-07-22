package com.analyzer.api.repository;

import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.enums.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerPlanRepository extends JpaRepository<CustomerPlan, Long> {
    Optional<CustomerPlan> findByCustomerIdAndStatus(Long customerId, PlanStatus status);
    Optional<CustomerPlan> findTopByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, PlanStatus status);
    List<CustomerPlan> findByCustomerId(Long customerId);
    boolean existsByCustomerIdAndStatus(Long customerId, PlanStatus status);
    boolean existsBySubscriptionPlanId(Long subscriptionPlanId);
    List<CustomerPlan> findAllByStatusAndEndDateBefore(PlanStatus status, LocalDateTime endDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cp from CustomerPlan cp where cp.id = :id")
    Optional<CustomerPlan> findByIdForUpdate(@Param("id") Long id);
}
