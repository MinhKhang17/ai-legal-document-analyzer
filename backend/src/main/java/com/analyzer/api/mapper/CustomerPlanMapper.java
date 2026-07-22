package com.analyzer.api.mapper;

import com.analyzer.api.dto.customerplan.CustomerPlanResponseDTO;
import com.analyzer.api.entity.CustomerPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {SubscriptionPlanMapper.class})
public interface CustomerPlanMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "latestTransactionId", ignore = true)
    @Mapping(target = "latestTransactionCode", ignore = true)
    CustomerPlanResponseDTO toResponseDTO(CustomerPlan plan);
}
