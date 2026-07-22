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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @InjectMocks CustomerPlanServiceImpl service;

    @Test
    void missingSubscriptionIsResolvedToFreePlanIdempotently() {
        User customer = User.builder().id(42L).build();
        SubscriptionPlan free = SubscriptionPlan.builder()
                .id(1L).planType("FREE").price(BigDecimal.ZERO).durationDays(30).active(true).build();
        AtomicReference<CustomerPlan> activePlan = new AtomicReference<>();
        CustomerPlanResponseDTO response = new CustomerPlanResponseDTO();

        when(customerPlanRepository.findByCustomerIdAndStatus(42L, PlanStatus.ACTIVE))
                .thenAnswer(invocation -> Optional.ofNullable(activePlan.get()));
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(customer));
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")).thenReturn(Optional.of(free));
        when(customerPlanRepository.save(any(CustomerPlan.class))).thenAnswer(invocation -> {
            CustomerPlan saved = invocation.getArgument(0);
            activePlan.set(saved);
            return saved;
        });
        when(customerPlanMapper.toResponseDTO(any(CustomerPlan.class))).thenReturn(response);

        assertThat(service.getMyPlan(42L)).isSameAs(response);
        assertThat(service.getMyPlan(42L)).isSameAs(response);

        verify(customerPlanRepository, times(1)).save(any(CustomerPlan.class));
        verify(userRepository, times(1)).findByIdForUpdate(42L);
        assertThat(activePlan.get().getSubscriptionPlan()).isSameAs(free);
        assertThat(activePlan.get().getStatus()).isEqualTo(PlanStatus.ACTIVE);
    }
}
