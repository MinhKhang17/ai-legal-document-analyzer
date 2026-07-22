package com.analyzer.api.repository.revenue;
import org.springframework.stereotype.Repository;

import com.analyzer.api.entity.RevenueAdjustment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.*;

@Repository
public interface RevenueAdjustmentRepository extends JpaRepository<RevenueAdjustment, String> {
    List<RevenueAdjustment> findByAppliedPeriodIdAndExpertIdOrderByCreatedAtAsc(String periodId, Long expertId);

    @Query("select coalesce(sum(a.amount),0) from RevenueAdjustment a where a.appliedPeriod.id=:periodId and a.expert.id=:expertId")
    BigDecimal sumFor(@Param("periodId") String periodId, @Param("expertId") Long expertId);
}
