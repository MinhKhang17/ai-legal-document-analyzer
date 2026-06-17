package com.analyzer.api.service.impl;

import com.analyzer.api.dto.customerplan.CustomerPlanResponseDTO;
import com.analyzer.api.dto.customerplan.SubscribeRequestDTO;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
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

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerPlanServiceImpl implements CustomerPlanService {

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

        // Tạo hoặc cập nhật CustomerPlan trạng thái PENDING
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

        return customerPlanMapper.toResponseDTO(customerPlan);
    }

    @Override
    @Transactional
    public CustomerPlanResponseDTO getMyPlan(Long customerId) {
        // Lấy gói ACTIVE hoặc tự động chuyển sang EXPIRED nếu hết hạn
        CustomerPlan activePlan = getActivePlanOrUpdateIfExpired(customerId);
        if (activePlan == null) {
            // Nếu không có ACTIVE, trả về thông tin gói PENDING gần nhất
            activePlan = customerPlanRepository.findByCustomerIdAndStatus(customerId, PlanStatus.PENDING)
                    .orElse(null);
        }

        if (activePlan == null) {
            // Nếu không có cả ACTIVE lẫn PENDING, lấy bất kỳ gói nào gần nhất
            activePlan = customerPlanRepository.findByCustomerId(customerId).stream()
                    .sorted((p1, p2) -> p2.getUpdatedAt().compareTo(p1.getUpdatedAt()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Bạn chưa đăng ký gói dịch vụ nào"));
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
                activePlan.setStatus(PlanStatus.EXPIRED);
                activePlan = customerPlanRepository.save(activePlan);
                return null;
            }
        }
        return activePlan;
    }
}
