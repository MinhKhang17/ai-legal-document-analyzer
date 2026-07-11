package com.analyzer.api.service.subscription.impl;

import com.analyzer.api.dto.subscription.RefundRequestDTO;
import com.analyzer.api.dto.subscription.RefundResponseDTO;
import com.analyzer.api.dto.subscription.UpdateRefundStatusRequest;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.entity.RefundRequest;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.RefundStatus;
import com.analyzer.api.enums.PaymentStatus;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.exception.common.ConflictException;
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

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private static final EnumSet<RefundStatus> IN_PROGRESS_STATUSES = EnumSet.of(
            RefundStatus.REQUESTED, RefundStatus.APPROVED, RefundStatus.PROCESSING);

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerPlanRepository customerPlanRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RefundResponseDTO requestRefund(Long customerId, RefundRequestDTO request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay khach hang ID: " + customerId));
        PaymentTransaction paymentTransaction = paymentTransactionRepository.findByIdForUpdate(request.getPaymentTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay giao dich thanh toan ID: " + request.getPaymentTransactionId()));
        if (!paymentTransaction.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException("Ban khong co quyen yeu cau hoan tien giao dich nay");
        }
        if (paymentTransaction.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("Chi giao dich thanh toan thanh cong moi co the yeu cau hoan tien");
        }
        if (refundRequestRepository.existsByPaymentTransactionIdAndStatusIn(
                paymentTransaction.getId(), IN_PROGRESS_STATUSES)) {
            throw new ConflictException("Giao dich dang co mot yeu cau hoan tien chua xu ly xong");
        }

        BigDecimal reservedAmount = refundRequestRepository.sumReservedAmount(
                paymentTransaction.getId(), RefundStatus.REJECTED);
        BigDecimal remainingAmount = paymentTransaction.getAmount().subtract(reservedAmount);
        if (request.getAmount().compareTo(remainingAmount) > 0) {
            throw new ConflictException("So tien hoan vuot qua so tien con co the hoan: " + remainingAmount);
        }

        CustomerPlan customerPlan = paymentTransaction.getCustomerPlan();
        if (request.getCustomerPlanId() != null) {
            CustomerPlan requestedPlan = customerPlanRepository.findById(request.getCustomerPlanId())
                    .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay goi khach hang ID: " + request.getCustomerPlanId()));
            if (!requestedPlan.getCustomer().getId().equals(customerId)) {
                throw new ForbiddenException("Ban khong co quyen yeu cau hoan tien goi nay");
            }
            if (customerPlan == null || !customerPlan.getId().equals(requestedPlan.getId())) {
                throw new ConflictException("Goi dich vu khong thuoc giao dich thanh toan nay");
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

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponseDTO> getMyRefunds(Long customerId) {
        return refundRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponseDTO> getRefunds(RefundStatus status) {
        List<RefundRequest> refunds = status == null
                ? refundRequestRepository.findAllByOrderByCreatedAtDesc()
                : refundRequestRepository.findByStatusOrderByCreatedAtDesc(status);
        return refunds.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public RefundResponseDTO updateRefundStatus(Long refundId, UpdateRefundStatusRequest request) {
        RefundRequest refund = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay yeu cau hoan tien ID: " + refundId));
        validateTransition(refund.getStatus(), request.status());
        if (request.status() == RefundStatus.REJECTED
                && (request.adminNote() == null || request.adminNote().isBlank())) {
            throw new ConflictException("Can nhap ly do khi tu choi yeu cau hoan tien");
        }

        refund.setStatus(request.status());
        refund.setAdminNote(normalizeNote(request.adminNote()));
        RefundRequest savedRefund = refundRequestRepository.save(refund);

        if (request.status() == RefundStatus.COMPLETED) {
            completeRefund(savedRefund);
        }
        return toResponse(savedRefund);
    }

    private void validateTransition(RefundStatus currentStatus, RefundStatus targetStatus) {
        boolean valid = switch (currentStatus) {
            case REQUESTED -> targetStatus == RefundStatus.APPROVED || targetStatus == RefundStatus.REJECTED;
            case APPROVED -> targetStatus == RefundStatus.PROCESSING;
            case PROCESSING -> targetStatus == RefundStatus.COMPLETED;
            case REJECTED, COMPLETED -> false;
        };
        if (!valid) {
            throw new ConflictException("Khong the chuyen trang thai hoan tien tu "
                    + currentStatus + " sang " + targetStatus);
        }
    }

    private void completeRefund(RefundRequest refund) {
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(
                        refund.getPaymentTransaction().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay giao dich thanh toan"));
        BigDecimal completedAmount = refundRequestRepository.sumAmountByStatus(
                transaction.getId(), RefundStatus.COMPLETED);
        if (completedAmount.compareTo(transaction.getAmount()) == 0) {
            transaction.setPaymentStatus(PaymentStatus.REFUNDED);
            paymentTransactionRepository.save(transaction);

            CustomerPlan plan = transaction.getCustomerPlan();
            if (plan != null) {
                plan.setStatus(PlanStatus.CANCELLED);
                plan.setAutoRenew(false);
                plan.setCancelReason("Hoan tien toan bo giao dich " + transaction.getTransactionCode());
                customerPlanRepository.save(plan);
            }
        }
    }

    private String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
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
