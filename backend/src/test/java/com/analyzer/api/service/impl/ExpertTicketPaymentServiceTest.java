package com.analyzer.api.service.impl;

import com.analyzer.api.config.VnPayProperties;
import com.analyzer.api.dto.paymenttransaction.PaymentUrlResponseDTO;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.*;
import com.analyzer.api.mapper.PaymentTransactionMapper;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpertTicketPaymentServiceTest {
    @Mock PaymentTransactionRepository paymentRepository;
    @Mock CustomerPlanRepository customerPlanRepository;
    @Mock LegalTicketRepository ticketRepository;
    @Mock PaymentTransactionMapper mapper;
    @Mock VnPayProperties properties;

    private PaymentTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentTransactionServiceImpl(
                paymentRepository, customerPlanRepository, ticketRepository, mapper, properties);
        when(properties.getTmnCode()).thenReturn("TESTCODE");
        when(properties.getHashSecret()).thenReturn("test-secret");
        when(properties.getPayUrl()).thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        when(properties.getReturnUrl()).thenReturn("http://localhost:8080/api/v1/payment-transactions/vnpay-return");
        when(properties.getVersion()).thenReturn("2.1.0");
        when(properties.getCommand()).thenReturn("pay");
        when(properties.getCurrCode()).thenReturn("VND");
        when(properties.getOrderType()).thenReturn("other");
        when(properties.getLocale()).thenReturn("vn");
    }

    @Test
    void createsVnPayTransactionForAcceptedPaidExpertTicket() {
        User customer = User.builder().id(2L).build();
        LegalTicket ticket = LegalTicket.builder()
                .id("ticket-1")
                .createdBy(customer)
                .title("Rà soát hợp đồng thuê")
                .status(LegalTicketStatus.WAITING_PAYMENT)
                .pricingType(TicketPricingType.PAID)
                .quoteStatus(TicketQuoteStatus.ACCEPTED)
                .customerPaymentStatus(TicketPaymentStatus.UNPAID)
                .userPrice(BigDecimal.valueOf(150_000))
                .build();
        when(ticketRepository.findByIdForPaymentUpdate("ticket-1")).thenReturn(Optional.of(ticket));
        when(paymentRepository.findTopByLegalTicket_IdAndPaymentStatusOrderByCreatedAtDesc(
                "ticket-1", PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            transaction.setId(99L);
            return transaction;
        });

        PaymentUrlResponseDTO response = service.createExpertTicketVnPayPaymentUrl(
                "ticket-1", 2L, "127.0.0.1");

        assertEquals(99L, response.transactionId());
        assertTrue(response.paymentUrl().startsWith("https://sandbox.vnpayment.vn"));
        assertEquals(TicketPaymentStatus.PENDING, ticket.getCustomerPaymentStatus());
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        assertEquals(PaymentPurpose.EXPERT_TICKET, captor.getAllValues().get(0).getPaymentPurpose());
        assertEquals("ticket-1", captor.getAllValues().get(0).getLegalTicket().getId());
    }
}
