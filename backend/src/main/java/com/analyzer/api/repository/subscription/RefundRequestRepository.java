package com.analyzer.api.repository.subscription;

import com.analyzer.api.entity.RefundRequest;
import com.analyzer.api.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    List<RefundRequest> findByStatus(RefundStatus status);

    Optional<RefundRequest> findByIdAndRequestedById(Long id, Long requestedById);
    Optional<RefundRequest> findByConfirmationTokenHash(String confirmationTokenHash);
    Optional<RefundRequest> findByConfirmationUsedTokenHash(String confirmationUsedTokenHash);

    List<RefundRequest> findByRequestedByIdOrderByCreatedAtDesc(Long requestedById);

    List<RefundRequest> findAllByOrderByCreatedAtDesc();

    List<RefundRequest> findByStatusOrderByCreatedAtDesc(RefundStatus status);

    boolean existsByPaymentTransactionIdAndStatusIn(Long paymentTransactionId,
                                                     Collection<RefundStatus> statuses);

    @Query("""
            select coalesce(sum(refund.amount), 0)
            from RefundRequest refund
            where refund.paymentTransaction.id = :paymentTransactionId
              and refund.status <> :excludedStatus
            """)
    BigDecimal sumReservedAmount(@Param("paymentTransactionId") Long paymentTransactionId,
                                 @Param("excludedStatus") RefundStatus excludedStatus);

    @Query("""
            select coalesce(sum(refund.amount), 0)
            from RefundRequest refund
            where refund.paymentTransaction.id = :paymentTransactionId
              and refund.status = :status
            """)
    BigDecimal sumAmountByStatus(@Param("paymentTransactionId") Long paymentTransactionId,
                                 @Param("status") RefundStatus status);
}
