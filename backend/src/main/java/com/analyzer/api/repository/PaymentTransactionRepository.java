package com.analyzer.api.repository;

import com.analyzer.api.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByCustomerId(Long customerId);
    List<PaymentTransaction> findByCustomerPlanId(Long customerPlanId);
    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);
    boolean existsBySubscriptionPlanId(Long subscriptionPlanId);
}
