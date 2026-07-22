package com.analyzer.api.service.revenue;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.entity.User;

public interface FinancialAuditService {

    void record(String action, User actor, String entityType, String entityId, String oldJson, String newJson,
            String reason, String requestId);

    PageResponse<RevenuePayrollDtos.Audit> list(int page, int size);
}
