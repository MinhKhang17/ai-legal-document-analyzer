package com.analyzer.api.service.impl;

import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanRequestDTO;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanResponseDTO;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.mapper.SubscriptionPlanMapper;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import com.analyzer.api.repository.SubscriptionPlanRepository;
import com.analyzer.api.service.SubscriptionPlanService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CustomerPlanRepository customerPlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionPlanMapper subscriptionPlanMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SubscriptionPlanResponseDTO createPlan(SubscriptionPlanRequestDTO request) {
        applyLegacyDefaults(request, null);
        normalizeAndValidate(request);
        if (subscriptionPlanRepository.existsByPlanNameIgnoreCase(request.getPlanName())
                || subscriptionPlanRepository.existsByPlanTypeIgnoreCase(request.getPlanType())) {
            throw new ConflictException("SUBSCRIPTION_PLAN_NAME_ALREADY_EXISTS");
        }
        SubscriptionPlan plan = subscriptionPlanMapper.toEntity(request);
        plan.setMaxQuota(0); // legacy non-null DB column; no longer a product quota
        plan.setActive(request.getActive() == null || request.getActive());
        applyNewFields(plan, request);
        return saveOrConflict(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponseDTO> getActivePlans() {
        return subscriptionPlanRepository.findByActiveTrue().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanResponseDTO getPlanById(Long id) {
        return toResponse(subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ConflictException("SUBSCRIPTION_PLAN_NOT_FOUND")));
    }

    @Override
    @Transactional
    public SubscriptionPlanResponseDTO updatePlan(Long id, SubscriptionPlanRequestDTO request) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ConflictException("SUBSCRIPTION_PLAN_NOT_FOUND"));
        applyLegacyDefaults(request, plan);
        normalizeAndValidate(request);
        if ((!plan.getPlanName().equalsIgnoreCase(request.getPlanName())
                && subscriptionPlanRepository.existsByPlanNameIgnoreCase(request.getPlanName()))
                || (!plan.getPlanType().equalsIgnoreCase(request.getPlanType())
                && subscriptionPlanRepository.existsByPlanTypeIgnoreCase(request.getPlanType()))) {
            throw new ConflictException("SUBSCRIPTION_PLAN_NAME_ALREADY_EXISTS");
        }
        plan.setPlanName(request.getPlanName());
        plan.setPlanType(request.getPlanType());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setDurationDays(request.getDurationDays());
        if (request.getActive() != null) plan.setActive(request.getActive());
        applyNewFields(plan, request);
        return saveOrConflict(plan);
    }

    // The existsBy* checks above are TOCTOU-racy (two concurrent admin requests can both pass
    // them before either commits); uk_subscription_plan_name_lower / uk_subscription_plan_type_lower
    // are the real guarantee. Translate a DB-level violation into the same clean 409 instead of
    // letting a raw DataIntegrityViolationException reach the client as an unexpected 500.
    private SubscriptionPlanResponseDTO saveOrConflict(SubscriptionPlan plan) {
        try {
            // saveAndFlush, not save: an UPDATE on an already-persisted entity is normally
            // deferred to transaction commit by Hibernate's dirty checking, which would happen
            // after this method (and its catch block) has already returned. Flushing forces the
            // constraint check to run synchronously, inside the try block, for both create and update.
            return toResponse(subscriptionPlanRepository.saveAndFlush(plan));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("SUBSCRIPTION_PLAN_NAME_ALREADY_EXISTS");
        }
    }

    @Override
    @Transactional
    public void deletePlan(Long id) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ConflictException("SUBSCRIPTION_PLAN_NOT_FOUND"));
        if (customerPlanRepository.existsBySubscriptionPlanId(id)
                || paymentTransactionRepository.existsBySubscriptionPlanId(id)) {
            plan.setActive(false);
            subscriptionPlanRepository.save(plan);
        } else {
            subscriptionPlanRepository.delete(plan);
        }
    }

    private void normalizeAndValidate(SubscriptionPlanRequestDTO request) {
        request.setPlanType(firstText(request.getName(), request.getPlanType()));
        request.setPlanName(firstText(request.getDisplayName(), request.getPlanName()));
        request.setPrice(request.getPriceVnd() != null ? request.getPriceVnd() : request.getPrice());
        request.setDurationDays(first(request.getBillingCycleDays(), request.getDurationDays()));
        request.setAiQuota(first(request.getAiTokenLimit(), request.getAiQuota()));
        request.setTicketQuota(first(request.getExpertTicketLimit(), request.getTicketQuota()));
        if (request.getPlanType() == null || request.getPlanName() == null
                || request.getPrice() == null || request.getPrice().signum() < 0
                || request.getDurationDays() == null || request.getDurationDays() <= 0) {
            throw new ConflictException("INVALID_SUBSCRIPTION_PLAN");
        }
        Integer[] limits = {request.getAiQuota(), request.getStorageLimitMb(), request.getTicketQuota()};
        for (Integer limit : limits) {
            if (limit == null || limit < 0) throw new ConflictException("INVALID_SUBSCRIPTION_PLAN_LIMITS");
        }
    }

    private void applyLegacyDefaults(SubscriptionPlanRequestDTO request, SubscriptionPlan existing) {
        if (request.getAiTokenLimit() == null && request.getAiQuota() == null) request.setAiQuota(existing == null ? 0 : safe(existing.getAiQuota()));
        if (request.getExpertTicketLimit() == null && request.getTicketQuota() == null) request.setTicketQuota(existing == null ? 0 : safe(existing.getTicketQuota()));
        if (request.getStorageLimitMb() == null) request.setStorageLimitMb(existing == null ? 0 : safe(existing.getStorageLimitMb()));
        if (existing != null) {
            if (request.getAllowSystemErrorTicket() == null) request.setAllowSystemErrorTicket(existing.getAllowSystemErrorTicket());
            if (request.getAllowQueryErrorTicket() == null) request.setAllowQueryErrorTicket(existing.getAllowQueryErrorTicket());
            if (request.getAllowContactExpertTicket() == null) request.setAllowContactExpertTicket(existing.getAllowContactExpertTicket());
        }
    }

    private int safe(Integer value) { return value == null ? 0 : value; }

    private void applyNewFields(SubscriptionPlan plan, SubscriptionPlanRequestDTO request) {
        plan.setAiQuota(request.getAiQuota());
        plan.setTicketQuota(request.getTicketQuota());
        plan.setStorageLimitMb(request.getStorageLimitMb());
        if (request.getAllowSystemErrorTicket() != null) plan.setAllowSystemErrorTicket(request.getAllowSystemErrorTicket());
        if (request.getAllowQueryErrorTicket() != null) plan.setAllowQueryErrorTicket(request.getAllowQueryErrorTicket());
        if (request.getAllowContactExpertTicket() != null) plan.setAllowContactExpertTicket(request.getAllowContactExpertTicket());
        if (request.getFeatures() != null || plan.getId() == null) {
            try {
                plan.setFeatureLimitsJson(objectMapper.writeValueAsString(request.getFeatures() == null ? List.of() : request.getFeatures()));
            } catch (Exception exception) {
                throw new ConflictException("INVALID_SUBSCRIPTION_PLAN_FEATURES");
            }
        }
    }

    @Override
    public SubscriptionPlanResponseDTO toResponse(SubscriptionPlan plan) {
        SubscriptionPlanResponseDTO response = subscriptionPlanMapper.toResponseDTO(plan);
        response.setName(plan.getPlanType());
        response.setDisplayName(plan.getPlanName());
        response.setPriceVnd(plan.getPrice());
        response.setBillingCycleDays(plan.getDurationDays());
        response.setAiTokenLimit(plan.getAiQuota());
        response.setStorageLimitMb(plan.getStorageLimitMb());
        response.setExpertTicketLimit(plan.getTicketQuota());
        response.setAllowSystemErrorTicket(plan.getAllowSystemErrorTicket());
        response.setAllowQueryErrorTicket(plan.getAllowQueryErrorTicket());
        response.setAllowContactExpertTicket(plan.getAllowContactExpertTicket());
        try {
            response.setFeatures(plan.getFeatureLimitsJson() == null ? List.of()
                    : objectMapper.readValue(plan.getFeatureLimitsJson(), new TypeReference<List<String>>() {}));
        } catch (Exception ignored) {
            response.setFeatures(List.of());
        }
        return response;
    }

    private String firstText(String preferred, String legacy) {
        String value = preferred != null && !preferred.isBlank() ? preferred : legacy;
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer first(Integer preferred, Integer legacy) { return preferred != null ? preferred : legacy; }
}
