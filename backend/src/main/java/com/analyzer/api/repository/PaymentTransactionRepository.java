package com.analyzer.api.repository;

import com.analyzer.api.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByCustomerId(Long customerId);

    List<PaymentTransaction> findByCustomerPlanId(Long customerPlanId);

    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentTransaction payment where payment.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentTransaction payment where payment.transactionCode = :transactionCode")
    Optional<PaymentTransaction> findByTransactionCodeForUpdate(@Param("transactionCode") String transactionCode);

    boolean existsBySubscriptionPlanId(Long subscriptionPlanId);
}
