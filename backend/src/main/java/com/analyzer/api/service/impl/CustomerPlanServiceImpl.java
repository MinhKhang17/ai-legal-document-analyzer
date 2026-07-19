package com.analyzer.api.service.impl;

import com.analyzer.api.dto.customerplan.CustomerPlanResponseDTO;
import com.analyzer.api.dto.customerplan.SubscribeRequestDTO;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.PaymentMethod;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.enums.PaymentStatus;
import com.analyzer.api.mapper.CustomerPlanMapper;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import com.analyzer.api.repository.SubscriptionPlanRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.CustomerPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Override
    @Transactional
    public CustomerPlanResponseDTO subscribe(Long customerId, SubscribeRequestDTO request) {
        User user = userRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin khách hàng"));

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getSubscriptionPlanId())
                .orElseThrow(() -> new RuntimeException(
                        "Gói dịch vụ không tồn tại với id: " + request.getSubscriptionPlanId()));

        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new RuntimeException("Gói dịch vụ này hiện đã ngừng hoạt động");
        }

        CustomerPlan currentActivePlan = getActivePlanOrUpdateIfExpired(customerId);
        if (currentActivePlan != null && currentActivePlan.getSubscriptionPlan() != null) {
            int currentRank = planRank(currentActivePlan.getSubscriptionPlan());
            int targetRank = planRank(plan);
            if (currentActivePlan.getSubscriptionPlan().getId().equals(plan.getId())) {
                throw new RuntimeException("CURRENT_SUBSCRIPTION_PLAN");
            }
            if (targetRank < currentRank) {
                currentActivePlan.setScheduledSubscriptionPlan(plan);
                currentActivePlan.setPlanChangeEffectiveAt(currentActivePlan.getEndDate());
                currentActivePlan.setAutoRenew(false);
                return customerPlanMapper.toResponseDTO(customerPlanRepository.save(currentActivePlan));
            }
        }

        // Tạo hoặc cập nhật CustomerPlan trạng thái PENDING
        if (plan.getPrice().signum() == 0) {
            LocalDateTime now = LocalDateTime.now();
            CustomerPlan freePlan = CustomerPlan.builder()
                    .customer(user).subscriptionPlan(plan).status(PlanStatus.ACTIVE)
                    .usedQuota(currentActivePlan == null ? 0 : currentActivePlan.getUsedQuota())
                    .startDate(now).endDate(now.plusDays(plan.getDurationDays()))
                    .usageStartAt(currentActivePlan == null ? now : currentActivePlan.getUsageStartAt())
                    .usageEndAt(currentActivePlan == null ? now.plusDays(plan.getDurationDays()) : currentActivePlan.getUsageEndAt())
                    .billingCycleStartAt(now).billingCycleEndAt(now.plusDays(plan.getDurationDays()))
                    .autoRenew(false).build();
            if (currentActivePlan != null) {
                currentActivePlan.setStatus(PlanStatus.EXPIRED);
                customerPlanRepository.save(currentActivePlan);
            }
            return customerPlanMapper.toResponseDTO(customerPlanRepository.save(freePlan));
        }

        if (request.getPaymentMethod() == PaymentMethod.VNPAY && !isValidVnPayAmount(plan.getPrice())) {
            throw new RuntimeException("So tien thanh toan VNPAY phai tu 5,000 VND den duoi 1 ty VND");
        }

        CustomerPlan customerPlan = customerPlanRepository.findByCustomerIdAndStatus(customerId, PlanStatus.PENDING)
                .orElse(null);

        if (customerPlan != null) {
            // Cập nhật CustomerPlan PENDING cũ
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

        CustomerPlanResponseDTO response = customerPlanMapper.toResponseDTO(customerPlan);
        response.setLatestTransactionId(transaction.getId());
        response.setLatestTransactionCode(transaction.getTransactionCode());
        return response;
    }

    @Override
    @Transactional
    public CustomerPlanResponseDTO getMyPlan(Long customerId) {
        // Chỉ gói ACTIVE mới được xem là gói người dùng đang sở hữu.
        CustomerPlan activePlan = getActivePlanOrUpdateIfExpired(customerId);
        if (activePlan == null) {
            throw new RuntimeException("Bạn chưa sở hữu gói dịch vụ nào đang kích hoạt");
        }

        return customerPlanMapper.toResponseDTO(activePlan);
    }

    @Override
    @Transactional
    public CustomerPlanResponseDTO cancelPlan(Long customerId, Long customerPlanId) {
        CustomerPlan plan = customerPlanRepository.findById(customerPlanId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói của khách hàng với id: " + customerPlanId));

        if (!plan.getCustomer().getId().equals(customerId)) {
            throw new RuntimeException("Bạn không có quyền hủy gói dịch vụ này");
        }

        plan.setStatus(PlanStatus.CANCELLED);
        CustomerPlan savedPlan = customerPlanRepository.save(plan);
        return customerPlanMapper.toResponseDTO(savedPlan);
    }

    @Override
    @Transactional
    public void validateChatQuota(Long customerId) {
        CustomerPlan activePlan = getActivePlanOrUpdateIfExpired(customerId);
        if (activePlan == null) {
            throw new RuntimeException("Bạn không có gói dịch vụ nào đang kích hoạt");
        }

        if (activePlan.getSubscriptionPlan() != null) {
            int maxQuota = activePlan.getSubscriptionPlan().getMaxQuota();
            if (activePlan.getUsedQuota() >= maxQuota) {
                throw new RuntimeException("Vượt quá hạn mức sử dụng của gói (Quota exceeded)");
            }
        }
    }

    @Override
    @Transactional
    public void recordChatUsage(Long customerId) {
        CustomerPlan activePlan = getActivePlanOrUpdateIfExpired(customerId);
        if (activePlan == null) {
            throw new RuntimeException("Bạn không có gói dịch vụ nào đang kích hoạt");
        }

        if (activePlan.getSubscriptionPlan() != null) {
            int maxQuota = activePlan.getSubscriptionPlan().getMaxQuota();
            if (activePlan.getUsedQuota() >= maxQuota) {
                throw new RuntimeException("Vượt quá hạn mức sử dụng của gói (Quota exceeded)");
            }
        }

        activePlan.setUsedQuota(activePlan.getUsedQuota() + 1);
        customerPlanRepository.save(activePlan);
    }

    private CustomerPlan getActivePlanOrUpdateIfExpired(Long customerId) {
        CustomerPlan activePlan = customerPlanRepository.findByCustomerIdAndStatus(customerId, PlanStatus.ACTIVE)
                .orElse(null);

        if (activePlan != null && activePlan.getEndDate() != null) {
            if (LocalDateTime.now().isAfter(activePlan.getEndDate())) {
                if (activePlan.getScheduledSubscriptionPlan() != null) {
                    SubscriptionPlan scheduled = activePlan.getScheduledSubscriptionPlan();
                    LocalDateTime now = LocalDateTime.now();
                    activePlan.setSubscriptionPlan(scheduled);
                    activePlan.setStartDate(now);
                    activePlan.setEndDate(now.plusDays(scheduled.getDurationDays()));
                    activePlan.setUsageStartAt(now);
                    activePlan.setUsageEndAt(activePlan.getEndDate());
                    activePlan.setBillingCycleStartAt(now);
                    activePlan.setBillingCycleEndAt(activePlan.getEndDate());
                    activePlan.setScheduledSubscriptionPlan(null);
                    activePlan.setPlanChangeEffectiveAt(null);
                    activePlan.setStatus(PlanStatus.ACTIVE);
                    return customerPlanRepository.save(activePlan);
                }
                activePlan.setStatus(PlanStatus.EXPIRED);
                activePlan = customerPlanRepository.save(activePlan);
                return null;
            }
        }
        return activePlan;
    }

    private int planRank(SubscriptionPlan plan) {
        if (plan.getTier() != null) return plan.getTier().ordinal();
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
