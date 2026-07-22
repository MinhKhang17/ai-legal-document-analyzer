package com.analyzer.api.service.paymenttransaction;

import com.analyzer.api.dto.paymenttransaction.PaymentTransactionResponse;
import com.analyzer.api.dto.paymenttransaction.PaymentUrlResponse;

import java.util.Map;
import java.util.List;

public interface PaymentTransactionService {
    List<PaymentTransactionResponse> getMyTransactions(Long customerId);
    List<PaymentTransactionResponse> getAllTransactions();
    PaymentUrlResponse createVnPayPaymentUrl(Long transactionId, Long customerId, String clientIp);
    PaymentUrlResponse createExpertTicketVnPayPaymentUrl(String ticketId, Long customerId, String clientIp);
    PaymentTransactionResponse handleVnPayCallback(Map<String, String> callbackParams);
}
