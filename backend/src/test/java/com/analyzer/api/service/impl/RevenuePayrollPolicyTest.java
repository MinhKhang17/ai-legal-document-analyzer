package com.analyzer.api.service.impl;

import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.*;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.revenue.CommissionPolicyRepository;
import com.analyzer.api.repository.revenue.CommissionPolicyChangeRequestRepository;
import com.analyzer.api.repository.revenue.CommissionPolicyExpertNotificationRepository;
import com.analyzer.api.repository.revenue.EarlyPayoutRequestRepository;
import com.analyzer.api.repository.revenue.ExpertPayoutTransactionRepository;
import com.analyzer.api.repository.revenue.ExpertRevenueStatementRepository;
import com.analyzer.api.repository.*;
import com.analyzer.api.service.notification.EmailService;
import com.analyzer.api.service.revenue.FinancialAuditService;
import com.analyzer.api.service.revenue.impl.CommissionPolicyManagementServiceImpl;
import com.analyzer.api.service.revenue.impl.EarlyPayoutServiceImpl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RevenuePayrollPolicyTest {
    @Test void nextMonthChangeUsesFirstDayAndRequiresEmailVerification(){
        var policies=mock(CommissionPolicyRepository.class);var requests=mock(CommissionPolicyChangeRequestRepository.class);
        var users=mock(UserRepository.class);var email=mock(EmailService.class);
        User admin=User.builder().id(1L).firstName("Admin").lastName("One").email("admin@test.local").build();
        CommissionPolicy active=CommissionPolicy.builder().id("active").rate(new BigDecimal("0.20")).effectiveFrom(LocalDate.of(1970,1,1)).status(CommissionPolicyStatus.ACTIVE).build();
        when(users.findById(1L)).thenReturn(Optional.of(admin));when(policies.applicable(any(),anyCollection())).thenReturn(List.of(active));
        when(requests.save(any())).thenAnswer(i->{CommissionPolicyChangeRequest r=i.getArgument(0);r.setId("request-1");r.setStatus(CommissionChangeRequestStatus.PENDING_EMAIL_VERIFICATION);r.setRequestedAt(LocalDateTime.now());return r;});when(email.sendFinancialEmail(any(),any(),any())).thenReturn(true);
        var service=new CommissionPolicyManagementServiceImpl(policies,requests,mock(CommissionPolicyExpertNotificationRepository.class),mock(SystemNotificationRepository.class),users,email,mock(FinancialAuditService.class));
        var result=service.requestChange(1L,new RevenuePayrollDtos.CreateCommissionChange(new BigDecimal("0.25"),"Policy test",CommissionApplicationType.NEXT_MONTH));
        LocalDate expected=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).withDayOfMonth(1).plusMonths(1);
        assertEquals(expected,result.effectiveFrom());assertEquals(CommissionChangeRequestStatus.PENDING_EMAIL_VERIFICATION,result.status());
        verify(email).sendFinancialEmail(eq("admin@test.local"),any(),contains("requestId="));
    }

    @Test void ambiguousPoliciesAreRejectedInsteadOfRandomSelection(){
        var policies=mock(CommissionPolicyRepository.class);
        CommissionPolicy a=CommissionPolicy.builder().rate(new BigDecimal("0.1")).build(),b=CommissionPolicy.builder().rate(new BigDecimal("0.2")).build();
        when(policies.applicable(any(),anyCollection())).thenReturn(List.of(a,b));
        var service=new CommissionPolicyManagementServiceImpl(policies,mock(CommissionPolicyChangeRequestRepository.class),mock(CommissionPolicyExpertNotificationRepository.class),mock(SystemNotificationRepository.class),mock(UserRepository.class),mock(EmailService.class),mock(FinancialAuditService.class));
        assertEquals("COMMISSION_POLICY_INTEGRITY_ERROR",assertThrows(ConflictException.class,()->service.rateFor(LocalDate.now())).getMessage());
    }

    @Test void earlyPayoutCannotExceedAvailableConfirmedBalance(){
        var statements=mock(ExpertRevenueStatementRepository.class);var requests=mock(EarlyPayoutRequestRepository.class);User expert=User.builder().id(9L).build();RevenuePeriod period=RevenuePeriod.builder().id("p").periodCode("2026-07").build();
        ExpertRevenueStatement statement=ExpertRevenueStatement.builder().id("s").expert(expert).period(period).status(RevenueStatementStatus.CONFIRMED).finalPayout(new BigDecimal("100.00")).paidAmount(BigDecimal.ZERO).remainingAmount(new BigDecimal("100.00")).build();
        when(statements.lockById("s")).thenReturn(Optional.of(statement));when(requests.reserved(eq("s"),anyCollection())).thenReturn(BigDecimal.ZERO);
        var service=new EarlyPayoutServiceImpl(requests,statements,mock(ExpertPayoutTransactionRepository.class),mock(UserRepository.class),mock(SystemNotificationRepository.class),mock(EmailService.class),mock(FinancialAuditService.class));
        var input=new RevenuePayrollDtos.CreateEarlyPayout("s",new BigDecimal("101.00"),"Need payout",null,"idem");
        assertEquals("EARLY_PAYOUT_AMOUNT_EXCEEDED",assertThrows(ConflictException.class,()->service.create(9L,input)).getMessage());
    }
}
