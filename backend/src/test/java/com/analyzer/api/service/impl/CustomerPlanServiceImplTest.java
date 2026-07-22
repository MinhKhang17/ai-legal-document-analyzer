package com.analyzer.api.service.impl;

import com.analyzer.api.dto.customerplan.CustomerPlanResponseDTO;
import com.analyzer.api.dto.customerplan.SubscribeRequestDTO;
import com.analyzer.api.dto.subscription.SubscriptionQuotaUsageSummaryResponse;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.PaymentMethod;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.mapper.CustomerPlanMapper;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import com.analyzer.api.repository.SubscriptionPlanRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.SubscriptionQuotaService;
import com.analyzer.api.service.support.CustomerPlanExpiryHelper;
import com.analyzer.api.service.support.CustomerPlanSnapshotHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPlanServiceImplTest {

    @Mock CustomerPlanRepository customerPlanRepository;
    @Mock SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock CustomerPlanMapper customerPlanMapper;
    @Mock SubscriptionQuotaService subscriptionQuotaService;
    @Mock CustomerPlanExpiryHelper customerPlanExpiryHelper;
    @Mock CustomerPlanSnapshotHelper customerPlanSnapshotHelper;

    private CustomerPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerPlanServiceImpl(customerPlanRepository, subscriptionPlanRepository,
                userRepository, paymentTransactionRepository, customerPlanMapper,
                subscriptionQuotaService, customerPlanExpiryHelper, customerPlanSnapshotHelper);
    }

    // --- cancelPlan(): self-service, graceful cancel (PLAN-CAN-01/02/05/06/09, muc 19 #1) ---

    @Test
    void cancelPlanSchedulesFreeDowngradeInsteadOfCuttingAccessImmediately() {
        User customer = User.builder().id(7L).build();
        SubscriptionPlan premium = SubscriptionPlan.builder().id(2L).planType("PREMIUM").active(true).build();
        SubscriptionPlan free = SubscriptionPlan.builder().id(1L).planType("FREE").active(true).durationDays(30).build();
        LocalDateTime endDate = LocalDateTime.now().plusDays(10);
        CustomerPlan paidPlan = CustomerPlan.builder().id(5L).customer(customer).subscriptionPlan(premium)
                .status(PlanStatus.ACTIVE).usedQuota(12).autoRenew(true).endDate(endDate).build();
        CustomerPlanResponseDTO response = new CustomerPlanResponseDTO();

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));
        when(customerPlanRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(paidPlan));
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")).thenReturn(Optional.of(free));
        when(customerPlanRepository.save(any(CustomerPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionQuotaService.getCurrentUsage(customer)).thenReturn(
                SubscriptionQuotaUsageSummaryResponse.builder().contractAnalysisUsed(3).contractAnalysisLimit(200).build());
        when(customerPlanMapper.toResponseDTO(any(CustomerPlan.class))).thenReturn(response);

        assertThat(service.cancelPlan(7L, 5L)).isSameAs(response);

        // Entitlement is NOT cut: status stays ACTIVE, plan/quota unchanged for the rest of the cycle.
        assertThat(paidPlan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(paidPlan.getSubscriptionPlan()).isSameAs(premium);
        assertThat(paidPlan.getUsedQuota()).isEqualTo(12);
        // ...only scheduled to fall back to FREE at the end of the already-paid cycle.
        assertThat(paidPlan.getScheduledSubscriptionPlan()).isSameAs(free);
        assertThat(paidPlan.getPlanChangeEffectiveAt()).isEqualTo(endDate);
        assertThat(paidPlan.getAutoRenew()).isFalse();
        assertThat(paidPlan.getCancelReason()).isNotBlank();
        verify(customerPlanRepository, times(1)).save(paidPlan);
    }

    @Test
    void cancelPlanRetryIsIdempotent() {
        User customer = User.builder().id(7L).build();
        SubscriptionPlan premium = SubscriptionPlan.builder().id(2L).planType("PREMIUM").active(true).build();
        SubscriptionPlan free = SubscriptionPlan.builder().id(1L).planType("FREE").active(true).durationDays(30).build();
        LocalDateTime endDate = LocalDateTime.now().plusDays(10);
        // Already scheduled from a first cancel call.
        CustomerPlan paidPlan = CustomerPlan.builder().id(5L).customer(customer).subscriptionPlan(premium)
                .status(PlanStatus.ACTIVE).endDate(endDate).scheduledSubscriptionPlan(free)
                .planChangeEffectiveAt(endDate).autoRenew(false).cancelReason("Cancelled by customer, effective at end of paid cycle").build();
        CustomerPlanResponseDTO response = new CustomerPlanResponseDTO();

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));
        when(customerPlanRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(paidPlan));
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")).thenReturn(Optional.of(free));
        when(customerPlanRepository.save(any(CustomerPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionQuotaService.getCurrentUsage(customer)).thenReturn(
                SubscriptionQuotaUsageSummaryResponse.builder().contractAnalysisUsed(0).contractAnalysisLimit(200).build());
        when(customerPlanMapper.toResponseDTO(any(CustomerPlan.class))).thenReturn(response);

        assertThat(service.cancelPlan(7L, 5L)).isSameAs(response);

        assertThat(paidPlan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(paidPlan.getScheduledSubscriptionPlan()).isSameAs(free);
    }

    @Test
    void cancelPlanRejectsFreePlan() {
        User customer = User.builder().id(7L).build();
        SubscriptionPlan free = SubscriptionPlan.builder().id(1L).planType("FREE").active(true).build();
        CustomerPlan freePlan = CustomerPlan.builder().id(5L).customer(customer).subscriptionPlan(free)
                .status(PlanStatus.ACTIVE).build();

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));
        when(customerPlanRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(freePlan));

        assertThatThrownBy(() -> service.cancelPlan(7L, 5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("FREE_PLAN_CANNOT_BE_CANCELLED");
        verify(customerPlanRepository, never()).save(any());
    }

    @Test
    void cancelPlanRejectsNonActivePlan() {
        User customer = User.builder().id(7L).build();
        SubscriptionPlan premium = SubscriptionPlan.builder().id(2L).planType("PREMIUM").active(true).build();
        CustomerPlan expiredPlan = CustomerPlan.builder().id(5L).customer(customer).subscriptionPlan(premium)
                .status(PlanStatus.EXPIRED).build();

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));
        when(customerPlanRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(expiredPlan));

        assertThatThrownBy(() -> service.cancelPlan(7L, 5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CUSTOMER_PLAN_NOT_ACTIVE");
    }

    @Test
    void cancelPlanRejectsWrongOwner() {
        User owner = User.builder().id(99L).build();
        SubscriptionPlan premium = SubscriptionPlan.builder().id(2L).planType("PREMIUM").active(true).build();
        CustomerPlan plan = CustomerPlan.builder().id(5L).customer(owner).subscriptionPlan(premium)
                .status(PlanStatus.ACTIVE).build();
        User caller = User.builder().id(7L).build();

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(caller));
        when(customerPlanRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.cancelPlan(7L, 5L)).isInstanceOf(ForbiddenException.class);
    }

    // --- cancelPlanAndActivateFree(): hard, immediate cancel (refund completion only) ---

    @Test
    void cancelPlanAndActivateFreeCutsAccessImmediatelyAndPreservesCurrentUsage() {
        User customer = User.builder().id(7L).build();
        SubscriptionPlan premium = SubscriptionPlan.builder().id(2L).planType("PREMIUM").active(true).build();
        SubscriptionPlan free = SubscriptionPlan.builder().id(1L).planType("FREE").active(true).durationDays(30).build();
        LocalDateTime usageStart = LocalDateTime.now().minusDays(5);
        LocalDateTime usageEnd = LocalDateTime.now().plusDays(25);
        CustomerPlan paidPlan = CustomerPlan.builder().id(5L).customer(customer).subscriptionPlan(premium)
                .status(PlanStatus.ACTIVE).usedQuota(12).autoRenew(true)
                .usageStartAt(usageStart).usageEndAt(usageEnd).build();
        CustomerPlanResponseDTO response = new CustomerPlanResponseDTO();

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));
        when(customerPlanRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(paidPlan));
        when(customerPlanRepository.findTopByCustomerIdAndStatusOrderByCreatedAtDesc(7L, PlanStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")).thenReturn(Optional.of(free));
        when(customerPlanRepository.save(any(CustomerPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionQuotaService.getCurrentUsage(customer)).thenReturn(
                SubscriptionQuotaUsageSummaryResponse.builder().contractAnalysisUsed(0).contractAnalysisLimit(5).build());
        when(customerPlanMapper.toResponseDTO(any(CustomerPlan.class))).thenReturn(response);

        assertThat(service.cancelPlanAndActivateFree(7L, 5L, "Refund TX-1")).isSameAs(response);

        assertThat(paidPlan.getStatus()).isEqualTo(PlanStatus.CANCELLED);
        assertThat(paidPlan.getAutoRenew()).isFalse();
        assertThat(paidPlan.getCancelReason()).isEqualTo("Refund TX-1");

        ArgumentCaptor<CustomerPlan> captor = ArgumentCaptor.forClass(CustomerPlan.class);
        verify(customerPlanRepository, times(2)).save(captor.capture());
        CustomerPlan freePlan = captor.getAllValues().get(1);
        assertThat(freePlan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(freePlan.getSubscriptionPlan()).isSameAs(free);
        assertThat(freePlan.getUsedQuota()).isEqualTo(12);
        assertThat(freePlan.getUsageStartAt()).isEqualTo(usageStart);
        assertThat(freePlan.getUsageEndAt()).isEqualTo(usageEnd);
        assertThat(freePlan.getAutoRenew()).isFalse();
        verify(customerPlanSnapshotHelper).applySnapshot(freePlan, free);
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

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));
        when(customerPlanRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(refundedPlan));
        when(customerPlanRepository.findTopByCustomerIdAndStatusOrderByCreatedAtDesc(7L, PlanStatus.ACTIVE))
                .thenReturn(Optional.of(activePlan));
        when(customerPlanRepository.save(refundedPlan)).thenReturn(refundedPlan);
        when(subscriptionQuotaService.getCurrentUsage(customer)).thenReturn(
                SubscriptionQuotaUsageSummaryResponse.builder().contractAnalysisUsed(0).contractAnalysisLimit(200).build());
        when(customerPlanMapper.toResponseDTO(activePlan)).thenReturn(response);

        assertThat(service.cancelPlanAndActivateFree(7L, 5L, "Refund TX-OLD")).isSameAs(response);

        assertThat(refundedPlan.getStatus()).isEqualTo(PlanStatus.CANCELLED);
        assertThat(activePlan.getSubscriptionPlan()).isSameAs(currentPremium);
        verify(subscriptionPlanRepository, never()).findByPlanTypeIgnoreCase("FREE");
        verifyNoInteractions(customerPlanSnapshotHelper);
    }

    // --- subscribe(): snapshot must be captured at subscribe() time, not later at callback ---

    @Test
    void subscribeToPaidPlanSnapshotsTermsBeforeCreatingThePendingTransaction() {
        User customer = User.builder().id(7L).build();
        SubscriptionPlan standard = SubscriptionPlan.builder().id(2L).planType("STANDARD").active(true)
                .price(BigDecimal.valueOf(79000)).durationDays(30).build();
        SubscribeRequestDTO request = new SubscribeRequestDTO();
        request.setSubscriptionPlanId(2L);
        request.setPaymentMethod(PaymentMethod.VNPAY);
        CustomerPlanResponseDTO response = new CustomerPlanResponseDTO();

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));
        when(subscriptionPlanRepository.findById(2L)).thenReturn(Optional.of(standard));
        when(customerPlanExpiryHelper.getActiveOrHandleExpiry(7L)).thenReturn(null);
        when(customerPlanRepository.findByCustomerIdAndStatus(7L, PlanStatus.PENDING)).thenReturn(Optional.empty());
        when(customerPlanRepository.save(any(CustomerPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerPlanMapper.toResponseDTO(any(CustomerPlan.class))).thenReturn(response);

        service.subscribe(7L, request);

        ArgumentCaptor<CustomerPlan> captor = ArgumentCaptor.forClass(CustomerPlan.class);
        verify(customerPlanRepository).save(captor.capture());
        CustomerPlan pendingPlan = captor.getValue();
        assertThat(pendingPlan.getStatus()).isEqualTo(PlanStatus.PENDING);
        // Snapshot is applied before save(), i.e. before the PaymentTransaction (amount = plan.getPrice()
        // at this same instant) is created — both freeze the same moment's terms.
        verify(customerPlanSnapshotHelper).applySnapshot(pendingPlan, standard);
        verify(paymentTransactionRepository).save(any());
    }

    @Test
    void missingSubscriptionIsResolvedToFreePlanIdempotently() {
        User customer = User.builder().id(42L).build();
        SubscriptionPlan free = SubscriptionPlan.builder()
                .id(1L).planType("FREE").price(BigDecimal.ZERO).durationDays(30).active(true).build();
        AtomicReference<CustomerPlan> activePlan = new AtomicReference<>();
        CustomerPlanResponseDTO response = new CustomerPlanResponseDTO();

        when(customerPlanExpiryHelper.getActiveOrHandleExpiry(42L)).thenAnswer(
                invocation -> activePlan.get());
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(customer));
        when(subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")).thenReturn(Optional.of(free));
        when(customerPlanRepository.save(any(CustomerPlan.class))).thenAnswer(invocation -> {
            CustomerPlan saved = invocation.getArgument(0);
            activePlan.set(saved);
            return saved;
        });
        when(subscriptionQuotaService.getCurrentUsage(customer)).thenReturn(
                SubscriptionQuotaUsageSummaryResponse.builder()
                        .contractAnalysisUsed(0).contractAnalysisLimit(5).build());
        when(customerPlanMapper.toResponseDTO(any(CustomerPlan.class))).thenReturn(response);

        assertThat(service.getMyPlan(42L)).isSameAs(response);
        assertThat(service.getMyPlan(42L)).isSameAs(response);

        verify(customerPlanRepository, times(1)).save(any(CustomerPlan.class));
        verify(userRepository, times(1)).findByIdForUpdate(42L);
        verify(customerPlanSnapshotHelper).applySnapshot(activePlan.get(), free);
        assertThat(activePlan.get().getSubscriptionPlan()).isSameAs(free);
        assertThat(activePlan.get().getStatus()).isEqualTo(PlanStatus.ACTIVE);
    }
}
