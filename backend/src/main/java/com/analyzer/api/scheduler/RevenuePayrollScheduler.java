package com.analyzer.api.scheduler;
import com.analyzer.api.service.CommissionPolicyManagementService;
import com.analyzer.api.service.RevenuePayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class RevenuePayrollScheduler {
 private final CommissionPolicyManagementService commission;
 private final RevenuePayrollService payroll;
 @Scheduled(cron="0 5 0 * * *",zone="Asia/Ho_Chi_Minh") public void activateCommission(){ commission.activateDuePolicies(); }
 @Scheduled(cron="0 0 */6 * * *",zone="Asia/Ho_Chi_Minh") public void retryNotifications(){ commission.retryFailed(); }
 @Scheduled(cron="0 1 0 1 * *",zone="Asia/Ho_Chi_Minh") public void createMonthlyPeriod(){ payroll.ensureCurrentPeriod(); }
 @Scheduled(cron="0 10 0 * * *",zone="Asia/Ho_Chi_Minh") public void generateDraftStatements(){ payroll.generateDraftStatements(); }
}
