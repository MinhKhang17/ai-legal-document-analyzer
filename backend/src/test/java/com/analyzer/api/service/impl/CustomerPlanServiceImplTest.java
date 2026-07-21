package com.analyzer.api.service.impl;

import com.analyzer.api.dto.customerplan.CustomerPlanResponseDTO;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.mapper.CustomerPlanMapper;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import com.analyzer.api.repository.SubscriptionPlanRepository;
import com.analyzer.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPlanServiceImplTest {

    @Mock CustomerPlanRepository customerPlanRepository;
    @Mock SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock CustomerPlanMapper customerPlanMapper;

    private CustomerPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerPlanServiceImpl(customerPlanRepository, subscriptionPlanRepository,
                userRepository, paymentTransactionRepository, customerPlanMapper);
    }

    @Test
    void cancellingPaidPlanActivatesFreePlanAndPreservesCurrentUsage() {
        User customer = User.builder().id(7L).build();
        SubscriptionPlan premium = SubscriptionPlan.builder().id(2L).planType("PREMIUM").active(true).build();
        SubscriptionPlan free = SubscriptionPlan.builder().id(1L).planType("FREE").active(true).durationDays(30).build();
        LocalDateTime usageStart = LocalDateTime.now().minusDays(5);
        LocalDateTime usageEnd = LocalDateTime.now().plusDays(25);
        CustomerPlan paidPlan = CustomerPlan.builder().id(5L).customer(customer).subscriptionPlan(premium)
                .status(PlanStatus.ACTIVE).usedQuota(12).autoRenew(true)
                .usageStartAt(usageStart).usageEndAt(usageEnd).build();
        CustomerPlanResponseDTO response = new CustomerPlanResponseDTO();

        when(customerPlanRepository.findById(5L)).thenReturn(Optional.of(paidPlan));
        when(customerPlanRepository.findTopByCustomerIdAndStatusOrderByCreatedAtDesc(7L, PlanStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")).thenReturn(Optional.of(free));
        when(customerPlanRepository.save(any(CustomerPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerPlanMapper.toResponseDTO(any(CustomerPlan.class))).thenReturn(response);

        assertSame(response, service.cancelPlan(7L, 5L));

        assertEquals(PlanStatus.CANCELLED, paidPlan.getStatus());
        assertFalse(paidPlan.getAutoRenew());
        assertEquals("Cancelled by customer", paidPlan.getCancelReason());

        ArgumentCaptor<CustomerPlan> captor = ArgumentCaptor.forClass(CustomerPlan.class);
        verify(customerPlanRepository, times(2)).save(captor.capture());
        CustomerPlan freePlan = captor.getAllValues().get(1);
        assertEquals(PlanStatus.ACTIVE, freePlan.getStatus());
        assertSame(free, freePlan.getSubscriptionPlan());
        assertEquals(12, freePlan.getUsedQuota());
        assertEquals(usageStart, freePlan.getUsageStartAt());
        assertEquals(usageEnd, freePlan.getUsageEndAt());
        assertFalse(freePlan.getAutoRenew());
    }

    @Test
    void refundingOldPlanDoesNotReplaceAnotherActivePlan() {
        User customer = User.builder().id(7L).build();
        SubscriptionPlan oldPremium = SubscriptionPlan.builder().id(2L).planType("PREMIUM").active(true).build();
        SubscriptionPlan currentPremium = SubscriptionPlan.builder().id(3L).planType("PREMIUM").active(true).build();
        CustomerPlan refundedPlan = CustomerPlan.builder().id(5L).customer(customer).subscriptionPlan(oldPremium)
                .status(PlanStatus.EXPIRED).usedQuota(4).autoRenew(false).build();
        CustomerPlan activePlan = CustomerPlan.builder().id(8L).customer(customer).subscriptionPlan(currentPremium)
                .status(PlanStatus.ACTIVE).usedQuota(1).autoRenew(true).build();
        CustomerPlanResponseDTO response = new CustomerPlanResponseDTO();

        when(customerPlanRepository.findById(5L)).thenReturn(Optional.of(refundedPlan));
        when(customerPlanRepository.findTopByCustomerIdAndStatusOrderByCreatedAtDesc(7L, PlanStatus.ACTIVE))
                .thenReturn(Optional.of(activePlan));
        when(customerPlanRepository.save(refundedPlan)).thenReturn(refundedPlan);
        when(customerPlanMapper.toResponseDTO(activePlan)).thenReturn(response);

        assertSame(response, service.cancelPlanAndActivateFree(7L, 5L, "Refund TX-OLD"));

        assertEquals(PlanStatus.CANCELLED, refundedPlan.getStatus());
        assertSame(currentPremium, activePlan.getSubscriptionPlan());
        verify(subscriptionPlanRepository, never()).findByPlanTypeIgnoreCase("FREE");
    }
}
