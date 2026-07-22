package com.analyzer.api.repository.revenue;
import org.springframework.stereotype.Repository;

import com.analyzer.api.entity.ExpertRevenueStatement;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

@Repository
public interface ExpertRevenueStatementRepository extends JpaRepository<ExpertRevenueStatement, String> {
    Optional<ExpertRevenueStatement> findByPeriodIdAndExpertId(String periodId, Long expertId);

    List<ExpertRevenueStatement> findByPeriodIdOrderByExpertNameSnapshotAsc(String periodId);

    Page<ExpertRevenueStatement> findByExpertIdOrderByPeriodStartDateDesc(Long expertId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExpertRevenueStatement s where s.id=:id")
    Optional<ExpertRevenueStatement> lockById(@Param("id") String id);
}
