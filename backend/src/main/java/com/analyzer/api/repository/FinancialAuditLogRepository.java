package com.analyzer.api.repository;
import com.analyzer.api.entity.FinancialAuditLog; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface FinancialAuditLogRepository extends JpaRepository<FinancialAuditLog,String>{ Page<FinancialAuditLog> findAllByOrderByCreatedAtDesc(Pageable p); }
