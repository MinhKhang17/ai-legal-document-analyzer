package com.analyzer.api.repository;

import com.analyzer.api.entity.TicketAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketAuditLogRepository extends JpaRepository<TicketAuditLog, String> {}
