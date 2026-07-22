package com.analyzer.api.mapper;

import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanRequest;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanResponse;
import com.analyzer.api.entity.SubscriptionPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SubscriptionPlanMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "tier", ignore = true)
    @Mapping(target = "featureLimitsJson", ignore = true)
    @Mapping(target = "storageLimitMb", ignore = true)
    @Mapping(target = "maxFileSizeMb", ignore = true)
    @Mapping(target = "maxAttachedDocumentsPerSession", ignore = true)
    @Mapping(target = "allowSystemErrorTicket", ignore = true)
    @Mapping(target = "allowQueryErrorTicket", ignore = true)
    @Mapping(target = "allowContactExpertTicket", ignore = true)
    SubscriptionPlan toEntity(SubscriptionPlanRequest requestDTO);

    @Mapping(target = "name", ignore = true)
    @Mapping(target = "displayName", ignore = true)
    @Mapping(target = "priceVnd", ignore = true)
    @Mapping(target = "billingCycleDays", ignore = true)
    @Mapping(target = "contractAnalysisLimit", ignore = true)
    @Mapping(target = "aiTokenLimit", ignore = true)
    @Mapping(target = "workspaceLimit", ignore = true)
    @Mapping(target = "documentPerWorkspaceLimit", ignore = true)
    @Mapping(target = "contractDraftLimit", ignore = true)
    @Mapping(target = "expertTicketLimit", ignore = true)
    @Mapping(target = "features", ignore = true)
    SubscriptionPlanResponse toResponseDTO(SubscriptionPlan plan);
}
