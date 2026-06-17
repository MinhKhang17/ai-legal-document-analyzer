package com.analyzer.api.service;

import com.analyzer.api.dto.paymenttransaction.PaymentTransactionResponseDTO;
import java.util.List;

public interface PaymentTransactionService {
    List<PaymentTransactionResponseDTO> getMyTransactions(Long customerId);
    List<PaymentTransactionResponseDTO> getAllTransactions();
    PaymentTransactionResponseDTO simulateSuccess(Long transactionId);
    PaymentTransactionResponseDTO simulateFailed(Long transactionId);
}
