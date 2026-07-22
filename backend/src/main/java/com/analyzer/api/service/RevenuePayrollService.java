package com.analyzer.api.service;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.entity.LegalTicket;

import java.math.BigDecimal;
import java.util.List;

public interface RevenuePayrollService {

    void recognizeResolvedTicket(LegalTicket ticket);

    void reconcileTicketFinancialChange(LegalTicket ticket, BigDecimal previousPayout, com.analyzer.api.entity.User actor,
            String reason);

    RevenuePayrollDtos.Period ensureCurrentPeriod();

    void generateDraftStatements();

    PageResponse<RevenuePayrollDtos.Period> periods(int page, int size);

    RevenuePayrollDtos.Period period(String id);

    List<RevenuePayrollDtos.Statement> periodStatements(String id);

    RevenuePayrollDtos.Statement adminStatement(String id);

    PageResponse<RevenuePayrollDtos.Statement> expertStatements(Long expertId, int page, int size);

    RevenuePayrollDtos.Statement expertStatement(Long expertId, String id);

    RevenuePayrollDtos.Period calculate(String periodId, Long adminId);

    RevenuePayrollDtos.Period close(String periodId, Long adminId);

    RevenuePayrollDtos.Adjustment addAdjustment(String periodId, Long adminId, RevenuePayrollDtos.CreateAdjustment input);

    RevenuePayrollDtos.Statement markRegularPaymentPending(String statementId, Long adminId);

    RevenuePayrollDtos.Statement markRegularPaid(String statementId, Long adminId, RevenuePayrollDtos.MarkStatementPayment input);
}
