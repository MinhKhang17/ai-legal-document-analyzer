package com.analyzer.api.repository.revenue;

import com.analyzer.api.entity.FinancialAuditLog;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancialAuditLogRepository extends JpaRepository<FinancialAuditLog, String> {
    Page<FinancialAuditLog> findAllByOrderByCreatedAtDesc(Pageable p);
}
