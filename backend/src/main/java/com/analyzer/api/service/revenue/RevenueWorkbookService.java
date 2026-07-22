package com.analyzer.api.service.revenue;

public interface RevenueWorkbookService {

    byte[] adminPeriod(String periodId, Long expertId);

    byte[] expertStatement(Long expertId, String statementId);
}
