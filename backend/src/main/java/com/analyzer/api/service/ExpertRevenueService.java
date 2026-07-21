package com.analyzer.api.service;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.AdminRevenueOverviewResponse;
import com.analyzer.api.dto.revenue.ExpertRevenueSummaryResponse;
import com.analyzer.api.dto.revenue.ExpertRevenueTicketResponse;
import com.analyzer.api.dto.revenue.UpdateExpertPaymentRequest;
import com.analyzer.api.entity.LegalTicket;

public interface ExpertRevenueService {
    ExpertRevenueSummaryResponse getSummary(Long expertId);
    PageResponse<ExpertRevenueTicketResponse> getTickets(Long expertId, int page, int size);
    ExpertRevenueTicketResponse updatePayment(String ticketId, Long adminId, UpdateExpertPaymentRequest request);
    AdminRevenueOverviewResponse getAdminOverview();

    /**
     * Xoá consultationFee/commissionRate/platformFee/expertPayout/paymentStatus của ticket để có thể reassign
     * sang expert khác. Bị chặn nếu paymentStatus đang PAID (tiền đã thực sự chuyển, không được xoá dấu vết).
     */
    ExpertRevenueTicketResponse resetFinancials(String ticketId, Long adminId);

    /**
     * Snapshot commissionRate (nếu chưa có) và tính lại platformFee/expertPayout từ consultationFee hiện tại.
     * Gọi khi ticket chuyển sang RESOLVED/CLOSED hoặc khi consultationFee thay đổi.
     */
    void applyCommissionSnapshot(LegalTicket ticket);
}
