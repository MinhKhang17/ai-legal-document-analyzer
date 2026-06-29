package com.analyzer.api.service.subscription.impl;

import com.analyzer.api.dto.subscription.RefundRequestDTO;
import com.analyzer.api.dto.subscription.RefundResponseDTO;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.entity.RefundRequest;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.RefundStatus;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.subscription.RefundRequestRepository;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.subscription.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerPlanRepository customerPlanRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RefundResponseDTO requestRefund(Long customerId, RefundRequestDTO request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay khach hang ID: " + customerId));
        PaymentTransaction paymentTransaction = paymentTransactionRepository.findById(request.getPaymentTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay giao dich thanh toan ID: " + request.getPaymentTransactionId()));
        if (!paymentTransaction.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException("Ban khong co quyen yeu cau hoan tien giao dich nay");
        }

        CustomerPlan customerPlan = null;
        if (request.getCustomerPlanId() != null) {
            customerPlan = customerPlanRepository.findById(request.getCustomerPlanId())
                    .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay goi khach hang ID: " + request.getCustomerPlanId()));
            if (!customerPlan.getCustomer().getId().equals(customerId)) {
                throw new ForbiddenException("Ban khong co quyen yeu cau hoan tien goi nay");
            }
        }

        RefundRequest refundRequest = RefundRequest.builder()
                .paymentTransaction(paymentTransaction)
                .customerPlan(customerPlan)
                .requestedBy(customer)
                .reason(request.getReason().trim())
                .status(RefundStatus.REQUESTED)
                .amount(request.getAmount())
                .adminNote("Yeu cau hoan tien dang duoc xem xet")
                .build();
        return toResponse(refundRequestRepository.save(refundRequest));
    }

    @Override
    @Transactional(readOnly = true)
    public RefundResponseDTO getRefund(Long refundId) {
        RefundRequest refundRequest = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay yeu cau hoan tien ID: " + refundId));
        if (!canViewRefund(refundRequest)) {
            throw new ForbiddenException("Ban khong co quyen xem yeu cau hoan tien nay");
        }
        return toResponse(refundRequest);
    }

    private RefundResponseDTO toResponse(RefundRequest refundRequest) {
        return RefundResponseDTO.builder()
                .id(refundRequest.getId())
                .paymentTransactionId(refundRequest.getPaymentTransaction().getId())
                .customerPlanId(refundRequest.getCustomerPlan() == null ? null : refundRequest.getCustomerPlan().getId())
                .requestedById(refundRequest.getRequestedBy().getId())
                .reason(refundRequest.getReason())
                .status(refundRequest.getStatus())
                .amount(refundRequest.getAmount())
                .adminNote(refundRequest.getAdminNote())
                .createdAt(refundRequest.getCreatedAt())
                .updatedAt(refundRequest.getUpdatedAt())
                .build();
    }

    private boolean canViewRefund(RefundRequest refundRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            return false;
        }
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return isAdmin || refundRequest.getRequestedBy().getId().equals(userDetails.getId());
    }
}
