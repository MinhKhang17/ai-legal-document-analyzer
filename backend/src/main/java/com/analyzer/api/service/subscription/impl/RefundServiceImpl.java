package com.analyzer.api.service.subscription.impl;

import com.analyzer.api.dto.subscription.RefundRequestDTO;
import com.analyzer.api.dto.subscription.RefundResponseDTO;
import com.analyzer.api.enums.RefundStatus;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.service.subscription.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    @Override
    public RefundResponseDTO requestRefund(Long customerId, RefundRequestDTO request) {
        return RefundResponseDTO.builder()
                .id(1L)
                .paymentTransactionId(request.getPaymentTransactionId())
                .customerPlanId(request.getCustomerPlanId())
                .requestedById(customerId)
                .reason(request.getReason())
                .status(RefundStatus.REQUESTED)
                .amount(request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO)
                .adminNote("Yêu cầu hoàn tiền đang được xem xét")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public RefundResponseDTO getRefund(Long refundId) {
        if (refundId == null || refundId <= 0) {
            throw new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền ID: " + refundId);
        }
        return RefundResponseDTO.builder()
                .id(refundId)
                .paymentTransactionId(100L)
                .customerPlanId(1L)
                .requestedById(2L)
                .reason("Khách hàng yêu cầu hủy dịch vụ")
                .status(RefundStatus.REQUESTED)
                .amount(new BigDecimal("150000"))
                .adminNote("Đang xử lý")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
