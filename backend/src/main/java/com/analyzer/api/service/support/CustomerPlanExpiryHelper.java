package com.analyzer.api.service.support;

import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.customerplan.CustomerPlanRepository;
import com.analyzer.api.repository.subscriptionplan.SubscriptionPlanRepository;
import com.analyzer.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CustomerPlanExpiryHelper {

    private static final Logger logger = LoggerFactory.getLogger(CustomerPlanExpiryHelper.class);

    private final CustomerPlanRepository customerPlanRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CustomerPlanSnapshotHelper customerPlanSnapshotHelper;

    @Transactional
    public CustomerPlan getActiveOrHandleExpiry(Long customerId) {
        CustomerPlan activePlan = customerPlanRepository.findByCustomerIdAndStatus(customerId, PlanStatus.ACTIVE)
                .orElse(null);
        if (activePlan == null) {
            return null;
        }

        // Data-integrity guard: every activation path (subscribe/markSuccess/downgrade)
        // always
        // sets both startDate and endDate together. A row missing endDate or with
        // startDate > endDate can only be corrupt data — treat it as a clear, detected
        // error
        // instead of silently granting indefinite access (a null endDate previously
        // fell
        // through the expiry check below and was treated as "never expires").
        if (activePlan.getEndDate() == null) {
            throw new ConflictException(
                    "CUSTOMER_PLAN_DATA_INTEGRITY_ERROR: missing endDate for plan " + activePlan.getId());
        }
        if (activePlan.getStartDate() != null && activePlan.getStartDate().isAfter(activePlan.getEndDate())) {
            throw new ConflictException(
                    "CUSTOMER_PLAN_DATA_INTEGRITY_ERROR: startDate after endDate for plan " + activePlan.getId());
        }

        LocalDateTime now = AppClock.now();
        if (activePlan.getStartDate() != null && now.isBefore(activePlan.getStartDate())) {
            // Not started yet — never granted by any current activation path, but don't
            // hand
            // out early access if a future-dated row exists (e.g. bad data or a future
            // feature).
            return null;
        }

        if (!now.isAfter(activePlan.getEndDate())) {
            return activePlan;
        }

        CustomerPlan locked = customerPlanRepository.findByIdForUpdate(activePlan.getId()).orElse(null);
        if (locked == null || locked.getStatus() != PlanStatus.ACTIVE) {
            return null;
        }
        if (!AppClock.now().isAfter(locked.getEndDate())) {
            return locked;
        }

        applyExpiryOrScheduledChange(locked);
        locked = customerPlanRepository.save(locked);
        return locked.getStatus() == PlanStatus.EXPIRED ? null : locked;
    }

    @Transactional
    public void expireDuePlans() {
        List<CustomerPlan> duePlans = customerPlanRepository
                .findAllByStatusAndEndDateBefore(PlanStatus.ACTIVE, AppClock.now());
        if (duePlans.isEmpty()) {
            return;
        }

        int processed = 0;
        for (CustomerPlan candidate : duePlans) {
            CustomerPlan locked = customerPlanRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (locked == null || locked.getStatus() != PlanStatus.ACTIVE
                    || !AppClock.now().isAfter(locked.getEndDate())) {
                continue;
            }
            applyExpiryOrScheduledChange(locked);
            customerPlanRepository.save(locked);
            processed++;
        }
        logger.info("Processed {} due customer plan(s) on schedule", processed);
    }

    // Mutates plan in place: applies the scheduled downgrade if one is pending,
    // otherwise marks it EXPIRED.
    public void applyExpiryOrScheduledChange(CustomerPlan plan) {
        SubscriptionPlan scheduled = plan.getScheduledSubscriptionPlan();

        if (scheduled != null && !Boolean.TRUE.equals(scheduled.getActive())) {
            logger.warn("Scheduled plan '{}' for customer_plan {} was disabled before it took "
                    + "effect; falling back to FREE instead of activating a disabled plan.",
                    scheduled.getPlanType(), plan.getId());
            scheduled = subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")
                    .filter(free -> Boolean.TRUE.equals(free.getActive()))
                    .orElse(null);
        }

        if (scheduled != null) {
            LocalDateTime now = AppClock.now();
            plan.setSubscriptionPlan(scheduled);
            plan.setStartDate(now);
            plan.setEndDate(now.plusDays(scheduled.getDurationDays()));
            plan.setUsageStartAt(now);
            plan.setUsageEndAt(plan.getEndDate());
            plan.setBillingCycleStartAt(now);
            plan.setBillingCycleEndAt(plan.getEndDate());
            plan.setScheduledSubscriptionPlan(null);
            plan.setPlanChangeEffectiveAt(null);
            plan.setCancelReason(null);
            plan.setStatus(PlanStatus.ACTIVE);
            customerPlanSnapshotHelper.applySnapshot(plan, scheduled);
        } else {
            plan.setScheduledSubscriptionPlan(null);
            plan.setPlanChangeEffectiveAt(null);
            plan.setStatus(PlanStatus.EXPIRED);
        }
    }
}
