package com.analyzer.api.service.impl;

import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanRequestDTO;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanResponseDTO;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.mapper.SubscriptionPlanMapper;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import com.analyzer.api.repository.SubscriptionPlanRepository;
import com.analyzer.api.service.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CustomerPlanRepository customerPlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionPlanMapper subscriptionPlanMapper;

    @Override
    @Transactional
    public SubscriptionPlanResponseDTO createPlan(SubscriptionPlanRequestDTO request) {
        if (subscriptionPlanRepository.existsByPlanName(request.getPlanName())) {
            throw new RuntimeException("Tên gói đã tồn tại");
        }

        SubscriptionPlan plan = subscriptionPlanMapper.toEntity(request);
        if (request.getActive() != null) {
            plan.setActive(request.getActive());
        } else {
            plan.setActive(true);
        }
        plan.setAiQuota(request.getAiQuota());
        plan.setTicketQuota(request.getTicketQuota());
        plan.setMaxWorkspaces(request.getMaxWorkspaces());
        plan.setMaxContractsPerWorkspace(request.getMaxContractsPerWorkspace());
        plan.setMaxDraftContracts(request.getMaxDraftContracts());

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);
        return subscriptionPlanMapper.toResponseDTO(savedPlan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponseDTO> getActivePlans() {
        return subscriptionPlanRepository.findByActiveTrue().stream()
                .map(subscriptionPlanMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanResponseDTO getPlanById(Long id) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói subscription với id: " + id));
        return subscriptionPlanMapper.toResponseDTO(plan);
    }

    @Override
    @Transactional
    public SubscriptionPlanResponseDTO updatePlan(Long id, SubscriptionPlanRequestDTO request) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói subscription với id: " + id));

        if (!plan.getPlanName().equalsIgnoreCase(request.getPlanName()) && 
                subscriptionPlanRepository.existsByPlanName(request.getPlanName())) {
            throw new RuntimeException("Tên gói đã tồn tại");
        }

        plan.setPlanName(request.getPlanName());
        plan.setPlanType(request.getPlanType());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setDurationDays(request.getDurationDays());
        plan.setMaxQuota(request.getMaxQuota());
        plan.setAiQuota(request.getAiQuota());
        plan.setTicketQuota(request.getTicketQuota());
        plan.setMaxWorkspaces(request.getMaxWorkspaces());
        plan.setMaxContractsPerWorkspace(request.getMaxContractsPerWorkspace());
        plan.setMaxDraftContracts(request.getMaxDraftContracts());
        if (request.getActive() != null) {
            plan.setActive(request.getActive());
        }

        SubscriptionPlan updatedPlan = subscriptionPlanRepository.save(plan);
        return subscriptionPlanMapper.toResponseDTO(updatedPlan);
    }

    @Override
    @Transactional
    public void deletePlan(Long id) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói subscription với id: " + id));

        boolean hasCustomerPlans = customerPlanRepository.existsBySubscriptionPlanId(id);
        boolean hasTransactions = paymentTransactionRepository.existsBySubscriptionPlanId(id);

        if (hasCustomerPlans || hasTransactions) {
            plan.setActive(false);
            subscriptionPlanRepository.save(plan);
        } else {
            subscriptionPlanRepository.delete(plan);
        }
    }
}
