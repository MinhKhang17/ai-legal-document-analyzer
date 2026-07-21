package com.analyzer.api.repository;
import com.analyzer.api.entity.RevenuePeriod;
import com.analyzer.api.enums.RevenuePeriodStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface RevenuePeriodRepository extends JpaRepository<RevenuePeriod,String>{
 Optional<RevenuePeriod> findByPeriodCode(String code);
 Optional<RevenuePeriod> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate a, LocalDate b);
 Optional<RevenuePeriod> findFirstByStatusOrderByStartDateDesc(RevenuePeriodStatus status);
 Page<RevenuePeriod> findAllByOrderByStartDateDesc(Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from RevenuePeriod p where p.id=:id") Optional<RevenuePeriod> lockById(@Param("id") String id);
}
