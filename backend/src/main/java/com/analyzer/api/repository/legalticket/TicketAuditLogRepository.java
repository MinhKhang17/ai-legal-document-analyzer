package com.analyzer.api.repository.legalticket;

import com.analyzer.api.entity.TicketAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketAuditLogRepository extends JpaRepository<TicketAuditLog, String> {}
