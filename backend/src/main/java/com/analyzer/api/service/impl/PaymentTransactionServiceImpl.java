package com.analyzer.api.service.impl;

import com.analyzer.api.dto.paymenttransaction.PaymentTransactionResponseDTO;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.enums.PaymentStatus;
import com.analyzer.api.mapper.PaymentTransactionMapper;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import com.analyzer.api.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentTransactionServiceImpl implements PaymentTransactionService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerPlanRepository customerPlanRepository;
    private final PaymentTransactionMapper paymentTransactionMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionResponseDTO> getMyTransactions(Long customerId) {
        return paymentTransactionRepository.findByCustomerId(customerId).stream()
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .map(paymentTransactionMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionResponseDTO> getAllTransactions() {
        return paymentTransactionRepository.findAll().stream()
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .map(paymentTransactionMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentTransactionResponseDTO simulateSuccess(Long transactionId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch thanh toán với id: " + transactionId));

        if (transaction.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException("Giao dịch này đã được xử lý trước đó");
        }

        // Cập nhật PaymentTransaction = SUCCESS
        transaction.setPaymentStatus(PaymentStatus.SUCCESS);
        transaction.setPaidAt(LocalDateTime.now());
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        // Cập nhật CustomerPlan = ACTIVE
        CustomerPlan customerPlan = transaction.getCustomerPlan();
        if (customerPlan != null) {
            Long customerId = transaction.getCustomer().getId();
            
            // Một Customer chỉ nên có một CustomerPlan ACTIVE tại một thời điểm.
            // Chuyển gói cũ đang ACTIVE (nếu có) thành EXPIRED.
            customerPlanRepository.findByCustomerIdAndStatus(customerId, PlanStatus.ACTIVE)
                    .ifPresent(oldActivePlan -> {
                        if (!oldActivePlan.getId().equals(customerPlan.getId())) {
                            oldActivePlan.setStatus(PlanStatus.EXPIRED);
                            customerPlanRepository.save(oldActivePlan);
                        }
                    });

            customerPlan.setStatus(PlanStatus.ACTIVE);
            customerPlan.setStartDate(LocalDateTime.now());
            if (transaction.getSubscriptionPlan() != null) {
                customerPlan.setEndDate(LocalDateTime.now().plusDays(transaction.getSubscriptionPlan().getDurationDays()));
            } else {
                customerPlan.setEndDate(LocalDateTime.now().plusDays(30)); // fallback
            }
            customerPlanRepository.save(customerPlan);
        }

        return paymentTransactionMapper.toResponseDTO(savedTransaction);
    }

    @Override
    @Transactional
    public PaymentTransactionResponseDTO simulateFailed(Long transactionId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch thanh toán với id: " + transactionId));

        if (transaction.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException("Giao dịch này đã được xử lý trước đó");
        }

        // Cập nhật PaymentTransaction = FAILED
        transaction.setPaymentStatus(PaymentStatus.FAILED);
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        // Giữ nguyên CustomerPlan ở trạng thái PENDING hoặc cập nhật thành CANCELLED/PENDING
        // Ở đây chúng ta giữ nguyên trạng thái PENDING như yêu cầu.
        
        return paymentTransactionMapper.toResponseDTO(savedTransaction);
    }
}
