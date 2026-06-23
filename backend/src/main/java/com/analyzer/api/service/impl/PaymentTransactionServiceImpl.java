package com.analyzer.api.service.impl;

import com.analyzer.api.dto.paymenttransaction.PaymentTransactionResponseDTO;
import com.analyzer.api.dto.paymenttransaction.PaymentUrlResponseDTO;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.enums.PaymentMethod;
import com.analyzer.api.enums.PaymentStatus;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.mapper.PaymentTransactionMapper;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import com.analyzer.api.service.PaymentTransactionService;
import com.analyzer.api.service.VnPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentTransactionServiceImpl implements PaymentTransactionService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerPlanRepository customerPlanRepository;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final VnPayService vnPayService;

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
    public PaymentUrlResponseDTO createVnPayPaymentUrl(Long transactionId, Long customerId, String clientIp) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay giao dich thanh toan voi id: " + transactionId));

        if (!transaction.getCustomer().getId().equals(customerId)) {
            throw new RuntimeException("Ban khong co quyen thanh toan giao dich nay");
        }
        if (transaction.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new RuntimeException("Giao dich nay khong su dung phuong thuc VNPAY");
        }
        if (transaction.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException("Giao dich nay da duoc xu ly truoc do");
        }

        String paymentUrl = vnPayService.createPaymentUrl(transaction, clientIp);
        transaction.setPaymentUrl(paymentUrl);
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        return new PaymentUrlResponseDTO(
                savedTransaction.getId(),
                savedTransaction.getTransactionCode(),
                PaymentMethod.VNPAY.name(),
                paymentUrl
        );
    }

    @Override
    @Transactional
    public PaymentTransactionResponseDTO handleVnPayCallback(Map<String, String> callbackParams) {
        if (!vnPayService.isValidSignature(callbackParams)) {
            throw new RuntimeException("Chu ky VNPAY khong hop le");
        }

        String transactionCode = callbackParams.get("vnp_TxnRef");
        PaymentTransaction transaction = paymentTransactionRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new RuntimeException("Khong tim thay giao dich VNPAY: " + transactionCode));

        transaction.setGatewayTransactionNo(callbackParams.get("vnp_TransactionNo"));
        transaction.setGatewayResponseCode(callbackParams.get("vnp_ResponseCode"));

        String responseCode = callbackParams.get("vnp_ResponseCode");
        String transactionStatus = callbackParams.get("vnp_TransactionStatus");
        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            transaction = markSuccess(transaction);
        } else {
            transaction = markFailed(transaction);
        }

        return paymentTransactionMapper.toResponseDTO(transaction);
    }

    @Override
    @Transactional
    public PaymentTransactionResponseDTO simulateSuccess(Long transactionId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay giao dich thanh toan voi id: " + transactionId));

        return paymentTransactionMapper.toResponseDTO(markSuccess(transaction));
    }

    @Override
    @Transactional
    public PaymentTransactionResponseDTO simulateFailed(Long transactionId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay giao dich thanh toan voi id: " + transactionId));

        return paymentTransactionMapper.toResponseDTO(markFailed(transaction));
    }

    private PaymentTransaction markSuccess(PaymentTransaction transaction) {
        if (transaction.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return transaction;
        }
        if (transaction.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException("Giao dich nay da duoc xu ly truoc do");
        }

        transaction.setPaymentStatus(PaymentStatus.SUCCESS);
        transaction.setPaidAt(LocalDateTime.now());
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        CustomerPlan customerPlan = transaction.getCustomerPlan();
        if (customerPlan != null) {
            Long customerId = transaction.getCustomer().getId();

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
                customerPlan.setEndDate(LocalDateTime.now().plusDays(30));
            }
            customerPlanRepository.save(customerPlan);
        }

        return savedTransaction;
    }

    private PaymentTransaction markFailed(PaymentTransaction transaction) {
        if (transaction.getPaymentStatus() == PaymentStatus.FAILED) {
            return transaction;
        }
        if (transaction.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException("Giao dich nay da duoc xu ly truoc do");
        }

        transaction.setPaymentStatus(PaymentStatus.FAILED);
        return paymentTransactionRepository.save(transaction);
    }
}
