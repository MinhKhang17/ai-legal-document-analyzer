package com.analyzer.api.service.subscription.impl;

import com.analyzer.api.dto.subscription.CreateRefundRequest;
import com.analyzer.api.dto.subscription.UpdateRefundStatusRequest;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.entity.RefundRequest;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.PaymentStatus;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.enums.RefundStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.customerplan.CustomerPlanRepository;
import com.analyzer.api.repository.paymenttransaction.PaymentTransactionRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.subscription.RefundRequestRepository;
import com.analyzer.api.service.customerplan.CustomerPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private CustomerPlanRepository customerPlanRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerPlanService customerPlanService;

    private RefundServiceImpl refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundServiceImpl(
                refundRequestRepository,
                paymentTransactionRepository,
                customerPlanRepository,
                userRepository,
                customerPlanService);
    }

    @Test
    void requestRefundRejectsAmountGreaterThanRemainingPayment() {
        User customer = User.builder().id(7L).build();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(11L)
                .customer(customer)
                .amount(new BigDecimal("100000"))
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();
        CreateRefundRequest request = CreateRefundRequest.builder()
                .paymentTransactionId(11L)
                .reason("Khong con nhu cau")
                .amount(new BigDecimal("30000"))
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(paymentTransactionRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(transaction));
        when(refundRequestRepository.existsByPaymentTransactionIdAndStatusIn(any(), any())).thenReturn(false);
        when(refundRequestRepository.sumReservedAmount(11L, RefundStatus.REJECTED))
                .thenReturn(new BigDecimal("80000"));

        assertThrows(ConflictException.class, () -> refundService.requestRefund(7L, request));
        verify(refundRequestRepository, never()).save(any());
    }

    @Test
    void completingFullRefundMarksPaymentRefundedAndCancelsPlan() {
        User customer = User.builder().id(7L).build();
        CustomerPlan plan = CustomerPlan.builder()
                .id(5L)
                .customer(customer)
                .status(PlanStatus.ACTIVE)
                .autoRenew(true)
                .build();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(11L)
                .customer(customer)
                .customerPlan(plan)
                .amount(new BigDecimal("100000"))
                .paymentStatus(PaymentStatus.SUCCESS)
                .transactionCode("PAY-11")
                .build();
        RefundRequest refund = RefundRequest.builder()
                .id(3L)
                .paymentTransaction(transaction)
                .customerPlan(plan)
                .requestedBy(customer)
                .reason("Khong con nhu cau")
                .amount(new BigDecimal("100000"))
                .status(RefundStatus.PROCESSING)
                .build();

        when(refundRequestRepository.findById(3L)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(transaction));
        when(refundRequestRepository.sumAmountByStatus(11L, RefundStatus.COMPLETED))
                .thenReturn(new BigDecimal("100000"));

        refundService.updateRefundStatus(3L,
                new UpdateRefundStatusRequest(RefundStatus.COMPLETED, "Da hoan thu cong"));

        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertEquals(PaymentStatus.REFUNDED, transaction.getPaymentStatus());
        verify(paymentTransactionRepository).save(transaction);
        verify(customerPlanService).cancelPlanAndActivateFree(7L, 5L, "Refund PAY-11");
    }

    @Test
    void completingPartialRefundKeepsPaymentAndPlanActive() {
        User customer = User.builder().id(7L).build();
        CustomerPlan plan = CustomerPlan.builder().id(5L).status(PlanStatus.ACTIVE).build();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(11L)
                .customer(customer)
                .customerPlan(plan)
                .amount(new BigDecimal("100000"))
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();
        RefundRequest refund = RefundRequest.builder()
                .id(3L)
                .paymentTransaction(transaction)
                .requestedBy(customer)
                .reason("Hoan mot phan")
                .amount(new BigDecimal("40000"))
                .status(RefundStatus.PROCESSING)
                .build();

        when(refundRequestRepository.findById(3L)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(transaction));
        when(refundRequestRepository.sumAmountByStatus(11L, RefundStatus.COMPLETED))
                .thenReturn(new BigDecimal("40000"));

        refundService.updateRefundStatus(3L,
                new UpdateRefundStatusRequest(RefundStatus.COMPLETED, null));

        assertEquals(PaymentStatus.SUCCESS, transaction.getPaymentStatus());
        assertEquals(PlanStatus.ACTIVE, plan.getStatus());
        verify(paymentTransactionRepository, never()).save(transaction);
        verify(customerPlanRepository, never()).save(plan);
        verify(customerPlanService, never()).cancelPlanAndActivateFree(any(), any(), any());
    }

    @Test
    void rejectedRefundCannotBeMovedToAnotherStatus() {
        RefundRequest refund = RefundRequest.builder().id(3L).status(RefundStatus.REJECTED).build();
        when(refundRequestRepository.findById(3L)).thenReturn(Optional.of(refund));

        assertThrows(ConflictException.class, () -> refundService.updateRefundStatus(
                3L, new UpdateRefundStatusRequest(RefundStatus.APPROVED, null)));
    }
}
