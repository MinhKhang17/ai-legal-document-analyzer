package com.analyzer.api.repository.revenue;
import org.springframework.stereotype.Repository;

import com.analyzer.api.entity.EarlyPayoutRequest;
import com.analyzer.api.enums.EarlyPayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.*;

@Repository
public interface EarlyPayoutRequestRepository extends JpaRepository<EarlyPayoutRequest, String> {
    Page<EarlyPayoutRequest> findByExpertIdOrderByRequestedAtDesc(Long expertId, Pageable p);

    Page<EarlyPayoutRequest> findAllByOrderByRequestedAtDesc(Pageable p);

    Optional<EarlyPayoutRequest> findByIdAndExpertId(String id, Long expertId);

    boolean existsByExpertIdAndPeriodIdAndStatusIn(Long expertId, String periodId,
            Collection<EarlyPayoutStatus> statuses);

    @Query("select coalesce(sum(coalesce(r.approvedAmount,r.requestedAmount)),0) from EarlyPayoutRequest r where r.statement.id=:statementId and r.status in :statuses")
    BigDecimal reserved(@Param("statementId") String statementId,
            @Param("statuses") Collection<EarlyPayoutStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from EarlyPayoutRequest r where r.id=:id")
    Optional<EarlyPayoutRequest> lockById(@Param("id") String id);
}
