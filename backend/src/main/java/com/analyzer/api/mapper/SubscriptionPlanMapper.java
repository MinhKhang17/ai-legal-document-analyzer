package com.analyzer.api.mapper;

import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanRequestDTO;
import com.analyzer.api.dto.subscriptionplan.SubscriptionPlanResponseDTO;
import com.analyzer.api.entity.SubscriptionPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SubscriptionPlanMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SubscriptionPlan toEntity(SubscriptionPlanRequestDTO requestDTO);

    SubscriptionPlanResponseDTO toResponseDTO(SubscriptionPlan plan);
}
