package com.analyzer.api.repository;
import com.analyzer.api.entity.CommissionPolicy; import com.analyzer.api.enums.CommissionPolicyStatus;
import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
import java.time.LocalDate; import java.util.*;
public interface CommissionPolicyRepository extends JpaRepository<CommissionPolicy,String>{
 @Query("select p from CommissionPolicy p where p.status in :statuses and p.effectiveFrom<=:date and (p.effectiveTo is null or p.effectiveTo>=:date) order by p.effectiveFrom desc") List<CommissionPolicy> applicable(@Param("date") LocalDate date,@Param("statuses") Collection<CommissionPolicyStatus> statuses);
 boolean existsByEffectiveFromAndStatusIn(LocalDate date,Collection<CommissionPolicyStatus> statuses);
 boolean existsByStatus(CommissionPolicyStatus status);
 List<CommissionPolicy> findAllByOrderByEffectiveFromDesc();
 List<CommissionPolicy> findByStatusAndEffectiveFromLessThanEqual(CommissionPolicyStatus status,LocalDate date);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from CommissionPolicy p where p.id=:id") Optional<CommissionPolicy> lockById(@Param("id") String id);
}
