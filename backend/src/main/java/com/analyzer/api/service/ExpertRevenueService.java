package com.analyzer.api.service;

import com.analyzer.api.dto.revenue.ExpertRevenueSummaryResponse;
import com.analyzer.api.dto.revenue.ExpertRevenueTicketResponse;
import com.analyzer.api.dto.revenue.UpdateExpertPaymentRequest;
import java.util.List;

public interface ExpertRevenueService {
    ExpertRevenueSummaryResponse getSummary(Long expertId);
    List<ExpertRevenueTicketResponse> getTickets(Long expertId);
    ExpertRevenueTicketResponse updatePayment(String ticketId, UpdateExpertPaymentRequest request);
}
