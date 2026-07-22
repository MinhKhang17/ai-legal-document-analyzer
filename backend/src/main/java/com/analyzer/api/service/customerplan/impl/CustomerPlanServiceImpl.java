package com.analyzer.api.service.customerplan.impl;

import com.analyzer.api.dto.customerplan.CustomerPlanResponse;
import com.analyzer.api.dto.customerplan.SubscribeRequest;
import com.analyzer.api.dto.subscription.SubscriptionQuotaUsageSummaryResponse;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.PaymentMethod;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.enums.PaymentStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.CustomerPlanMapper;
import com.analyzer.api.repository.customerplan.CustomerPlanRepository;
import com.analyzer.api.repository.paymenttransaction.PaymentTransactionRepository;
import com.analyzer.api.repository.subscriptionplan.SubscriptionPlanRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.service.customerplan.CustomerPlanService;
import com.analyzer.api.service.subscription.SubscriptionQuotaService;
import com.analyzer.api.service.support.CustomerPlanExpiryHelper;
import com.analyzer.api.service.support.CustomerPlanSnapshotHelper;
import com.analyzer.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerPlanServiceImpl implements CustomerPlanService {

    private static final BigDecimal VNPAY_MIN_AMOUNT = BigDecimal.valueOf(5000);
    private static final BigDecimal VNPAY_MAX_AMOUNT = BigDecimal.valueOf(1_000_000_000);

    private final CustomerPlanRepository customerPlanRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerPlanMapper customerPlanMapper;
    private final SubscriptionQuotaService subscriptionQuotaService;
    private final CustomerPlanExpiryHelper customerPlanExpiryHelper;
    private final CustomerPlanSnapshotHelper customerPlanSnapshotHelper;

    @Override
    @Transactional
    public CustomerPlanResponse subscribe(Long customerId, SubscribeRequest request) {

        User user = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin khách hàng"));

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getSubscriptionPlanId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Gói dịch vụ không tồn tại với id: " + request.getSubscriptionPlanId()));

        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new ConflictException("Gói dịch vụ này hiện đã ngừng hoạt động");
        }

        CustomerPlan currentActivePlan = getActivePlanOrUpdateIfExpired(customerId);
        if (currentActivePlan != null && currentActivePlan.getSubscriptionPlan() != null) {
            int currentRank = planRank(currentActivePlan.getSubscriptionPlan());
            int targetRank = planRank(plan);
            if (currentActivePlan.getSubscriptionPlan().getId().equals(plan.getId())) {
                throw new ConflictException("CURRENT_SUBSCRIPTION_PLAN");
            }
            if (targetRank < currentRank) {
                currentActivePlan.setScheduledSubscriptionPlan(plan);
                currentActivePlan.setPlanChangeEffectiveAt(currentActivePlan.getEndDate());
                currentActivePlan.setAutoRenew(false);
                return toResponseWithAccurateQuota(customerPlanRepository.save(currentActivePlan));
            }
        }

        // Tạo hoặc cập nhật CustomerPlan trạng thái PENDING
        if (plan.getPrice().signum() == 0) {
            LocalDateTime now = AppClock.now();
            CustomerPlan freePlan = CustomerPlan.builder()
                    .customer(user).subscriptionPlan(plan).status(PlanStatus.ACTIVE)
                    .usedQuota(currentActivePlan == null ? 0 : currentActivePlan.getUsedQuota())
                    .startDate(now).endDate(now.plusDays(plan.getDurationDays()))
                    .usageStartAt(currentActivePlan == null ? now : currentActivePlan.getUsageStartAt())
                    .usageEndAt(currentActivePlan == null ? now.plusDays(plan.getDurationDays())
                            : currentActivePlan.getUsageEndAt())
                    .billingCycleStartAt(now).billingCycleEndAt(now.plusDays(plan.getDurationDays()))
                    .autoRenew(false).build();
            customerPlanSnapshotHelper.applySnapshot(freePlan, plan);
            if (currentActivePlan != null) {
                currentActivePlan.setStatus(PlanStatus.EXPIRED);
                // Flush before inserting the new ACTIVE row: a DB constraint enforces at most
                // one ACTIVE customer_plan per customer, and Hibernate may otherwise order the
                // new row's INSERT before this row's UPDATE within the same flush, tripping it.
                customerPlanRepository.saveAndFlush(currentActivePlan);
            }
            return toResponseWithAccurateQuota(customerPlanRepository.save(freePlan));
        }

        if (request.getPaymentMethod() == PaymentMethod.VNPAY && !isValidVnPayAmount(plan.getPrice())) {
            throw new RuntimeException("So tien thanh toan VNPAY phai tu 5,000 VND den duoi 1 ty VND");
        }

        CustomerPlan customerPlan = customerPlanRepository.findByCustomerIdAndStatus(customerId, PlanStatus.PENDING)
                .orElse(null);

        if (customerPlan != null) {
            // Cập nhật CustomerPlan PENDING cũ sang lựa chọn gói mới. Hủy các giao dịch
            // PENDING cũ gắn với gói này trước — nếu không, một giao dịch bị bỏ dở (ứng
            // với lựa chọn gói trước đó) vẫn có thể được VNPay xác nhận sau này và kích
            // hoạt sai gói/sai thời hạn lên cùng một CustomerPlan đã bị đổi lựa chọn.
            cancelStalePendingTransactions(customerPlan);
            customerPlan.setSubscriptionPlan(plan);
            customerPlan.setUsedQuota(0);
            customerPlan.setStartDate(null);
            customerPlan.setEndDate(null);
            customerPlan.setAutoRenew(false);
        } else {
            // Tạo mới CustomerPlan PENDING
            customerPlan = CustomerPlan.builder()
                    .customer(user)
                    .subscriptionPlan(plan)
                    .status(PlanStatus.PENDING)
                    .usedQuota(0)
                    .autoRenew(false)
                    .build();
        }
        customerPlanSnapshotHelper.applySnapshot(customerPlan, plan);

        customerPlan = customerPlanRepository.save(customerPlan);

        // Tạo PaymentTransaction trạng thái PENDING
        String transactionCode = "TX" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .customer(user)
                .subscriptionPlan(plan)
                .customerPlan(customerPlan)
                .amount(plan.getPrice())
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .transactionCode(transactionCode)
                .build();

        paymentTransactionRepository.save(transaction);

        CustomerPlanResponse response = toResponseWithAccurateQuota(customerPlan);
        response.setLatestTransactionId(transaction.getId());
        response.setLatestTransactionCode(transaction.getTransactionCode());
        return response;
    }

    @Override
    @Transactional
    public CustomerPlanResponse getMyPlan(Long customerId) {
        // Chỉ gói ACTIVE mới được xem là gói người dùng đang sở hữu.
        CustomerPlan activePlan = getActivePlanOrUpdateIfExpired(customerId);
        if (activePlan == null) {
            activePlan = createDefaultFreePlan(customerId);
        }
        if (activePlan.getSubscriptionPlan() == null) {
            throw new ConflictException("SUBSCRIPTION_NOT_FOUND", "The active subscription has no plan");
        }
        if (!Boolean.TRUE.equals(activePlan.getSubscriptionPlan().getActive())) {
            throw new ConflictException("SUBSCRIPTION_INACTIVE", "The active subscription references an inactive plan");
        }

        return toResponseWithAccurateQuota(activePlan);
    }

    @Override
    @Transactional
    public CustomerPlanResponse cancelPlan(Long customerId, Long customerPlanId) {
        userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin khách hàng"));

        CustomerPlan plan = customerPlanRepository.findByIdForUpdate(customerPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói của khách hàng với id: " + customerPlanId));

        if (!plan.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException("Bạn không có quyền hủy gói dịch vụ này");
        }
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new ConflictException("CUSTOMER_PLAN_NOT_ACTIVE");
        }

        SubscriptionPlan currentSubscription = plan.getSubscriptionPlan();
        if (currentSubscription != null && "FREE".equalsIgnoreCase(currentSubscription.getPlanType())) {
            throw new ConflictException("FREE_PLAN_CANNOT_BE_CANCELLED");
        }

        SubscriptionPlan freeSubscription = subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")
                .filter(free -> Boolean.TRUE.equals(free.getActive()))
                .orElseThrow(() -> new ConflictException("FREE_PLAN_NOT_CONFIGURED"));

        plan.setScheduledSubscriptionPlan(freeSubscription);
        plan.setPlanChangeEffectiveAt(plan.getEndDate());
        plan.setAutoRenew(false);
        plan.setCancelReason("Cancelled by customer, effective at end of paid cycle");
        return toResponseWithAccurateQuota(customerPlanRepository.save(plan));
    }

    @Override
    @Transactional
    public CustomerPlanResponse cancelPlanAndActivateFree(Long customerId, Long customerPlanId, String reason) {
        // Lock the user row first (same order subscribe() uses) so cancel can't interleave
        // with a concurrent subscribe()/upgrade for the same customer, then lock the specific
        // CustomerPlan row so it can't interleave with a VNPay callback activating it (both
        // now go through CustomerPlanRepository.findByIdForUpdate on this row).
        userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin khách hàng"));

        CustomerPlan plan = customerPlanRepository.findByIdForUpdate(customerPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói của khách hàng với id: " + customerPlanId));

        if (!plan.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException("Bạn không có quyền hủy gói dịch vụ này");
        }

        SubscriptionPlan currentSubscription = plan.getSubscriptionPlan();
        if (currentSubscription != null && "FREE".equalsIgnoreCase(currentSubscription.getPlanType())) {
            plan.setStatus(PlanStatus.ACTIVE);
            plan.setAutoRenew(false);
            return toResponseWithAccurateQuota(customerPlanRepository.save(plan));
        }

        plan.setStatus(PlanStatus.CANCELLED);
        plan.setAutoRenew(false);
        plan.setCancelReason(reason);
        customerPlanRepository.save(plan);

        CustomerPlan otherActivePlan = customerPlanRepository
                .findTopByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, PlanStatus.ACTIVE)
                .filter(active -> !active.getId().equals(plan.getId()))
                .orElse(null);
        if (otherActivePlan != null) {
            return toResponseWithAccurateQuota(otherActivePlan);
        }

        SubscriptionPlan freeSubscription = subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")
                .filter(free -> Boolean.TRUE.equals(free.getActive()))
                .orElseThrow(() -> new ConflictException("FREE_PLAN_NOT_CONFIGURED"));
        LocalDateTime now = AppClock.now();
        LocalDateTime usageStart = plan.getUsageStartAt() != null ? plan.getUsageStartAt() : now;
        LocalDateTime usageEnd = plan.getUsageEndAt() != null && plan.getUsageEndAt().isAfter(now)
                ? plan.getUsageEndAt()
                : now.plusDays(freeSubscription.getDurationDays());
        CustomerPlan freePlan = CustomerPlan.builder()
                .customer(plan.getCustomer())
                .subscriptionPlan(freeSubscription)
                .status(PlanStatus.ACTIVE)
                .usedQuota(plan.getUsedQuota() == null ? 0 : plan.getUsedQuota())
                .startDate(now)
                .endDate(now.plusDays(freeSubscription.getDurationDays()))
                .usageStartAt(usageStart)
                .usageEndAt(usageEnd)
                .billingCycleStartAt(now)
                .billingCycleEndAt(now.plusDays(freeSubscription.getDurationDays()))
                .autoRenew(false)
                .build();
        customerPlanSnapshotHelper.applySnapshot(freePlan, freeSubscription);
        return toResponseWithAccurateQuota(customerPlanRepository.save(freePlan));
    }

    @Override
    @Transactional
    public void expireDuePlans() {
        customerPlanExpiryHelper.expireDuePlans();
    }

    private CustomerPlan getActivePlanOrUpdateIfExpired(Long customerId) {
        return customerPlanExpiryHelper.getActiveOrHandleExpiry(customerId);
    }

    private void cancelStalePendingTransactions(CustomerPlan customerPlan) {
        List<PaymentTransaction> stalePending = paymentTransactionRepository.findByCustomerPlanId(customerPlan.getId())
                .stream()
                .filter(t -> t.getPaymentStatus() == PaymentStatus.PENDING)
                .toList();
        if (stalePending.isEmpty()) {
            return;
        }
        stalePending.forEach(t -> t.setPaymentStatus(PaymentStatus.CANCELLED));
        paymentTransactionRepository.saveAll(stalePending);
    }

    private CustomerPlanResponse toResponseWithAccurateQuota(CustomerPlan plan) {
        CustomerPlanResponse dto = customerPlanMapper.toResponseDTO(plan);
        Integer maxQuota = plan.getAnalysisLimitSnapshot() != null
                ? plan.getAnalysisLimitSnapshot()
                : (plan.getSubscriptionPlan() != null ? plan.getSubscriptionPlan().getMaxQuota() : null);

        if (plan.getStatus() == PlanStatus.ACTIVE && plan.getCustomer() != null) {
            SubscriptionQuotaUsageSummaryResponse usage = subscriptionQuotaService.getCurrentUsage(plan.getCustomer());
            dto.setUsedQuota(usage.getContractAnalysisUsed());
            dto.setRemainingQuota(usage.getContractAnalysisLimit() == null
                    ? null
                    : Math.max(usage.getContractAnalysisLimit() - usage.getContractAnalysisUsed(), 0));
        } else {
            dto.setUsedQuota(0);
            dto.setRemainingQuota(maxQuota);
        }
        return dto;
    }

    private CustomerPlan createDefaultFreePlan(Long customerId) {
        User customer = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay thong tin khach hang"));

        CustomerPlan concurrentActivePlan = getActivePlanOrUpdateIfExpired(customerId);
        if (concurrentActivePlan != null) {
            return concurrentActivePlan;
        }

        SubscriptionPlan freePlan = subscriptionPlanRepository.findByPlanTypeIgnoreCase("FREE")
                .orElseThrow(() -> new ConflictException(
                        "SUBSCRIPTION_NOT_FOUND", "The default Free subscription is not configured"));
        if (!Boolean.TRUE.equals(freePlan.getActive())) {
            throw new ConflictException("SUBSCRIPTION_INACTIVE", "The default Free subscription is inactive");
        }

        LocalDateTime now = AppClock.now();
        LocalDateTime endDate = now.plusDays(freePlan.getDurationDays());
        CustomerPlan defaultPlan = CustomerPlan.builder()
                .customer(customer)
                .subscriptionPlan(freePlan)
                .status(PlanStatus.ACTIVE)
                .usedQuota(0)
                .startDate(now)
                .endDate(endDate)
                .usageStartAt(now)
                .usageEndAt(endDate)
                .billingCycleStartAt(now)
                .billingCycleEndAt(endDate)
                .autoRenew(false)
                .build();
        customerPlanSnapshotHelper.applySnapshot(defaultPlan, freePlan);
        return customerPlanRepository.save(defaultPlan);
    }

    private int planRank(SubscriptionPlan plan) {
        if (plan.getTier() != null)
            return plan.getTier().ordinal();
        return switch (plan.getPlanType().toUpperCase()) {
            case "PREMIUM" -> 2;
            case "STANDARD" -> 1;
            default -> 0;
        };
    }

    private boolean isValidVnPayAmount(BigDecimal amount) {
        return amount != null
                && amount.compareTo(VNPAY_MIN_AMOUNT) >= 0
                && amount.compareTo(VNPAY_MAX_AMOUNT) < 0;
    }
}
