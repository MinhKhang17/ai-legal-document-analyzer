package com.analyzer.api.repository.revenue;
import org.springframework.stereotype.Repository;

import com.analyzer.api.entity.CommissionPolicyChangeRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

@Repository
public interface CommissionPolicyChangeRequestRepository extends JpaRepository<CommissionPolicyChangeRequest, String> {
    Optional<CommissionPolicyChangeRequest> findByTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CommissionPolicyChangeRequest r where r.id=:id")
    Optional<CommissionPolicyChangeRequest> lockById(@Param("id") String id);
}
