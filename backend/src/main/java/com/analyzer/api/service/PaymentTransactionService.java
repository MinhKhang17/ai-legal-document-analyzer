package com.analyzer.api.service;

import com.analyzer.api.dto.paymenttransaction.PaymentTransactionResponseDTO;
import com.analyzer.api.dto.paymenttransaction.PaymentUrlResponseDTO;

import java.util.Map;
import java.util.List;

public interface PaymentTransactionService {
    List<PaymentTransactionResponseDTO> getMyTransactions(Long customerId);
    List<PaymentTransactionResponseDTO> getAllTransactions();
    PaymentUrlResponseDTO createVnPayPaymentUrl(Long transactionId, Long customerId, String clientIp);
    PaymentTransactionResponseDTO handleVnPayCallback(Map<String, String> callbackParams);
    PaymentTransactionResponseDTO simulateSuccess(Long transactionId);
    PaymentTransactionResponseDTO simulateFailed(Long transactionId);
}
