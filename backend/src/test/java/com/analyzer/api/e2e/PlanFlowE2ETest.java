package com.analyzer.api.e2e;

import com.analyzer.api.dto.customerplan.CustomerPlanResponse;
import com.analyzer.api.dto.customerplan.SubscribeRequest;
import com.analyzer.api.dto.subscription.SubscriptionQuotaUsageSummaryResponse;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanRequest;
import com.analyzer.api.dto.workspace.WorkspaceRequest;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.PaymentMethod;
import com.analyzer.api.enums.PaymentStatus;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The 8 priority E2E scenarios from Plan_Flow_Test_Cases.md muc 18, run against a real
// Testcontainers Postgres so the DB-level mechanisms (advisory locks, partial unique indexes,
// VNPay HMAC) are exercised for real instead of assumed from a mock.
class PlanFlowE2ETest extends AbstractPlanFlowE2ETest {

    @Autowired private TestUploadAttemptService testUploadAttemptService;

    // E2E-PLAN-01 (P0): new user, no CustomerPlan -> Free fallback (auto-provisioned on first
    // /my-plan read) -> workspace/document counts are unmetered (only AI tokens, expert
    // tickets and storage remain metered quotas) -> no Premium access.
    @Test
    void e2e01_newUserFallsBackToFreeAndCannotExceedItsQuota() {
        User user = createCustomer("e2e01");

        assertThat(subscriptionQuotaService.getCurrentPlan(user).getPlanType()).isEqualToIgnoringCase("FREE");
        CustomerPlanResponse myPlan = customerPlanService.getMyPlan(user.getId());
        assertThat(myPlan.getSubscriptionPlan().getPlanType()).isEqualToIgnoringCase("FREE");
        assertThat(myPlan.getStatus()).isEqualTo(PlanStatus.ACTIVE);

        String workspaceId = workspaceService.createWorkspace(user.getId(),
                new WorkspaceRequest("WS1", null)).workspaceId();

        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        for (int i = 0; i < 3; i++) {
            subscriptionQuotaService.checkCanUploadOrAnalyzeContract(user, workspace.getId(), 1_000_000L);
            createReadyDocument(workspace, user, LocalDateTime.now(), 1_000_000L);
        }

        assertThatThrownBy(() -> subscriptionQuotaService.checkCanCreateExpertTicket(user))
                .isInstanceOf(ForbiddenException.class)
                .extracting(error -> ((ForbiddenException) error).getErrorCode())
                .isEqualTo("EXPERT_TICKET_REQUIRES_PREMIUM");
    }

    // E2E-PLAN-02 (P0): Free -> buy Standard -> PENDING -> VNPay IPN success -> a duplicate
    // "return" callback for the same transaction arrives afterwards -> exactly one Standard
    // ACTIVE, previous plan EXPIRED, callback processing is idempotent.
    @Test
    void e2e02_duplicateVnPayCallbackIsIdempotentAfterSuccess() {
        User user = createCustomer("e2e02");
        SubscriptionPlan standard = planByType("STANDARD");

        SubscribeRequest request = new SubscribeRequest();
        request.setSubscriptionPlanId(standard.getId());
        request.setPaymentMethod(PaymentMethod.VNPAY);
        CustomerPlanResponse pending = customerPlanService.subscribe(user.getId(), request);
        PaymentTransaction transaction = paymentTransactionRepository.findById(pending.getLatestTransactionId()).orElseThrow();

        Map<String, String> callback = signedVnPayCallback(transaction.getTransactionCode(), transaction.getAmount(), "00", "00");

        paymentTransactionService.handleVnPayCallback(callback); // IPN
        paymentTransactionService.handleVnPayCallback(callback); // duplicate return, same params

        PaymentTransaction reloaded = paymentTransactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(reloaded.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

        List<CustomerPlan> allPlans = customerPlanRepository.findByCustomerId(user.getId());
        assertThat(allPlans.stream().filter(p -> p.getStatus() == PlanStatus.ACTIVE)).hasSize(1);
        CustomerPlan active = allPlans.stream().filter(p -> p.getStatus() == PlanStatus.ACTIVE).findFirst().orElseThrow();
        // Compare by id, not by navigating the lazy subscriptionPlan association: the session
        // that loaded `active` is already closed here (no Open-Session-In-View outside a real
        // HTTP request), and a lazy proxy's id is available without touching the DB, but any
        // other getter would throw LazyInitializationException.
        assertThat(active.getSubscriptionPlan().getId()).isEqualTo(standard.getId());
    }

    // E2E-PLAN-03 (P1): Standard has used 20 analyses / 500k tokens -> upgrade to Premium ->
    // usage stays 20/200 analyses and 500k/8.5M tokens (same billing cycle, just re-evaluated
    // against the new plan's limits).
    @Test
    void e2e03_upgradePreservesUsageAgainstNewLimits() {
        User user = createCustomer("e2e03");
        SubscriptionPlan standard = planByType("STANDARD");
        SubscriptionPlan premium = planByType("PREMIUM");
        LocalDateTime now = LocalDateTime.now();
        CustomerPlan standardPlan = persistCustomerPlan(user, standard, PlanStatus.ACTIVE, now.minusDays(5), now.plusDays(25));
        Workspace workspace = createWorkspace(user);

        for (int i = 0; i < 20; i++) {
            createReadyDocument(workspace, user, now, 100_000L);
        }
        recordCompletedAssistantTokens(user, workspace, 300_000);
        recordCompletedAssistantTokens(user, workspace, 200_000);

        SubscriptionQuotaUsageSummaryResponse beforeUpgrade = subscriptionQuotaService.getCurrentUsage(user);
        assertThat(beforeUpgrade.getAiTokensUsed()).isEqualTo(500_000);
        assertThat(beforeUpgrade.getStorageUsedBytes()).isEqualTo(2_000_000L);

        SubscribeRequest upgrade = new SubscribeRequest();
        upgrade.setSubscriptionPlanId(premium.getId());
        upgrade.setPaymentMethod(PaymentMethod.VNPAY);
        CustomerPlanResponse pending = customerPlanService.subscribe(user.getId(), upgrade);
        PaymentTransaction transaction = paymentTransactionRepository.findById(pending.getLatestTransactionId()).orElseThrow();
        paymentTransactionService.handleVnPayCallback(
                signedVnPayCallback(transaction.getTransactionCode(), transaction.getAmount(), "00", "00"));

        CustomerPlan oldStandardRow = customerPlanRepository.findById(standardPlan.getId()).orElseThrow();
        assertThat(oldStandardRow.getStatus()).isEqualTo(PlanStatus.EXPIRED);

        SubscriptionQuotaUsageSummaryResponse afterUpgrade = subscriptionQuotaService.getCurrentUsage(user);
        assertThat(afterUpgrade.getAiTokensUsed()).isEqualTo(500_000);
        assertThat(afterUpgrade.getAiTokensLimit()).isEqualTo(premium.getAiQuota());
        assertThat(afterUpgrade.getStorageUsedBytes()).isEqualTo(2_000_000L);
    }

    // E2E-PLAN-04 (P0): Premium scheduled to downgrade to Standard; endDate already passed;
    // the QUOTA service is the first thing to query the plan (not CustomerPlanService) ->
    // Standard ACTIVE, no Free fallback — both entry points share the same expiry logic.
    @Test
    void e2e04_scheduledDowngradeAppliesConsistentlyWhenQuotaServiceAsksFirst() {
        User user = createCustomer("e2e04");
        SubscriptionPlan premium = planByType("PREMIUM");
        SubscriptionPlan standard = planByType("STANDARD");
        LocalDateTime now = LocalDateTime.now();
        persistCustomerPlan(user, premium, PlanStatus.ACTIVE, now.minusDays(30), now.minusSeconds(1), standard, now.minusSeconds(1));

        // Quota service asks first (as opposed to CustomerPlanService.getMyPlan()).
        SubscriptionPlan resolved = subscriptionQuotaService.getCurrentPlan(user);
        assertThat(resolved.getPlanType()).isEqualToIgnoringCase("STANDARD");

        CustomerPlanResponse myPlan = customerPlanService.getMyPlan(user.getId());
        assertThat(myPlan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(myPlan.getSubscriptionPlan().getPlanType()).isEqualToIgnoringCase("STANDARD");

        assertThat(customerPlanRepository.findByCustomerId(user.getId()).stream()
                .filter(p -> p.getStatus() == PlanStatus.ACTIVE)).hasSize(1);
    }

    // E2E-PLAN-05 (P1, updated per business decision): Premium with 20 days left -> customer
    // cancels -> entitlement is KEPT until endDate (muc 19 #1 decision), so upload/expert-ticket
    // still work; only autoRenew/scheduledSubscriptionPlan reflect the pending fallback to Free.
    @Test
    void e2e05_cancelKeepsPremiumEntitlementUntilEndOfPaidCycle() {
        User user = createCustomer("e2e05");
        SubscriptionPlan premium = planByType("PREMIUM");
        LocalDateTime now = LocalDateTime.now();
        CustomerPlan plan = persistCustomerPlan(user, premium, PlanStatus.ACTIVE, now.minusDays(10), now.plusDays(20));
        Workspace workspace = createWorkspace(user);

        customerPlanService.cancelPlan(user.getId(), plan.getId());

        CustomerPlan reloaded = customerPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(reloaded.getScheduledSubscriptionPlan().getId()).isEqualTo(planByType("FREE").getId());
        assertThat(reloaded.getAutoRenew()).isFalse();

        // Still Premium entitlement for the rest of the paid cycle: 20MB upload and an expert
        // ticket both still succeed (neither would be allowed on Free).
        subscriptionQuotaService.checkCanUploadOrAnalyzeContract(user, workspace.getId(), 20L * 1024 * 1024);
        subscriptionQuotaService.checkCanCreateExpertTicket(user);
    }

    // E2E-PLAN-06 (P0): many concurrent VNPay callbacks for the same successful transaction ->
    // exactly one activation, no duplicate ACTIVE plan.
    @Test
    void e2e06_concurrentVnPayCallbacksActivateExactlyOnce() throws InterruptedException {
        User user = createCustomer("e2e06");
        SubscriptionPlan standard = planByType("STANDARD");
        SubscribeRequest request = new SubscribeRequest();
        request.setSubscriptionPlanId(standard.getId());
        request.setPaymentMethod(PaymentMethod.VNPAY);
        CustomerPlanResponse pending = customerPlanService.subscribe(user.getId(), request);
        PaymentTransaction transaction = paymentTransactionRepository.findById(pending.getLatestTransactionId()).orElseThrow();
        Map<String, String> callback = signedVnPayCallback(transaction.getTransactionCode(), transaction.getAmount(), "00", "00");

        int concurrency = 20;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger unexpectedErrors = new AtomicInteger();
        for (int i = 0; i < concurrency; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    paymentTransactionService.handleVnPayCallback(callback);
                } catch (Exception ex) {
                    unexpectedErrors.incrementAndGet();
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<CustomerPlan> plans = customerPlanRepository.findByCustomerId(user.getId());
        assertThat(plans.stream().filter(p -> p.getStatus() == PlanStatus.ACTIVE)).hasSize(1);
        PaymentTransaction reloaded = paymentTransactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(reloaded.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    // E2E-PLAN-07 (P0): Free workspace one upload away from the plan's storage limit -> many
    // concurrent upload attempts -> at most 1 succeeds, storage usage never exceeds the plan
    // limit. Adapted from the doc's literal "5/month" framing to a storage "last slot" (the
    // per-workspace document-count quota was retired; storage remains the only metered upload
    // limit) — it still exercises exactly the concurrency mechanism (UserQuotaLock) the case
    // is about.
    @Test
    void e2e07_concurrentUploadsWithOneSlotLeftNeverExceedTheLimit() throws InterruptedException {
        User user = createCustomer("e2e07");
        SubscriptionPlan free = planByType("FREE");
        LocalDateTime now = LocalDateTime.now();
        persistCustomerPlan(user, free, PlanStatus.ACTIVE, now.minusDays(1), now.plusDays(29));
        Workspace workspace = createWorkspace(user);
        long storageLimitBytes = free.getStorageLimitMb() * 1024L * 1024L;
        createReadyDocument(workspace, user, now, storageLimitBytes - 1_000_000L);

        int concurrency = 50;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        for (int i = 0; i < concurrency; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    testUploadAttemptService.checkAndRecordUpload(user, workspace, 1_000_000L);
                    succeeded.incrementAndGet();
                } catch (ConflictException ex) {
                    rejected.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(concurrency - 1);
        long finalStorageUsed = documentRepository.sumFileSizeByUserIdAndStatusNot(user.getId(), "DELETED");
        assertThat(finalStorageUsed).isEqualTo(storageLimitBytes);
    }

    // E2E-PLAN-08 (P0): Admin edits Premium's quota mid-cycle -> the existing ACTIVE customer
    // keeps their old (snapshotted) limit; a customer subscribing afterwards gets the new one.
    @Test
    void e2e08_adminPlanEditDoesNotRetroactivelyChangeAnAlreadyActiveCustomer() {
        User existingCustomer = createCustomer("e2e08-existing");
        SubscriptionPlan premium = planByType("PREMIUM");
        int originalAiQuota = premium.getAiQuota();
        LocalDateTime now = LocalDateTime.now();
        persistCustomerPlan(existingCustomer, premium, PlanStatus.ACTIVE, now.minusDays(5), now.plusDays(25));

        SubscriptionPlanRequest editRequest = new SubscriptionPlanRequest();
        editRequest.setPlanName(premium.getPlanName());
        editRequest.setPlanType(premium.getPlanType());
        editRequest.setPrice(premium.getPrice());
        editRequest.setDurationDays(premium.getDurationDays());
        editRequest.setAiQuota(originalAiQuota + 500_000);
        editRequest.setTicketQuota(premium.getTicketQuota());
        editRequest.setStorageLimitMb(premium.getStorageLimitMb());
        subscriptionPlanService.updatePlan(premium.getId(), editRequest);

        SubscriptionQuotaUsageSummaryResponse existingUsage = subscriptionQuotaService.getCurrentUsage(existingCustomer);
        assertThat(existingUsage.getAiTokensLimit()).isEqualTo(originalAiQuota);

        User newCustomer = createCustomer("e2e08-new");
        SubscribeRequest subscribeRequest = new SubscribeRequest();
        subscribeRequest.setSubscriptionPlanId(premium.getId());
        subscribeRequest.setPaymentMethod(PaymentMethod.VNPAY);
        CustomerPlanResponse pending = customerPlanService.subscribe(newCustomer.getId(), subscribeRequest);
        PaymentTransaction transaction = paymentTransactionRepository.findById(pending.getLatestTransactionId()).orElseThrow();
        paymentTransactionService.handleVnPayCallback(
                signedVnPayCallback(transaction.getTransactionCode(), transaction.getAmount(), "00", "00"));

        SubscriptionQuotaUsageSummaryResponse newUsage = subscriptionQuotaService.getCurrentUsage(newCustomer);
        assertThat(newUsage.getAiTokensLimit()).isEqualTo(originalAiQuota + 500_000);
    }
}
