package com.analyzer.api.repository;

import com.analyzer.api.entity.AiQueryExecution;
import com.analyzer.api.enums.AiQueryExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AiQueryExecutionRepository extends JpaRepository<AiQueryExecution, Long> {
    Optional<AiQueryExecution> findByRequestIdAndUserId(String requestId, Long userId);

    @Query("""
            select coalesce(sum(e.estimatedTokens), 0)
            from AiQueryExecution e
            where e.user.id = :userId
              and e.status = :status
              and e.createdAt >= :periodStart
              and e.createdAt < :periodEnd
              and e.requestId <> :excludedRequestId
            """)
    long sumReservedTokens(
            @Param("userId") Long userId,
            @Param("status") AiQueryExecutionStatus status,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("excludedRequestId") String excludedRequestId);
}
