package com.analyzer.api.mapper;

import com.analyzer.api.dto.paymenttransaction.PaymentTransactionResponseDTO;
import com.analyzer.api.entity.PaymentTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentTransactionMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "subscriptionPlanId", source = "subscriptionPlan.id")
    @Mapping(target = "planName", source = "subscriptionPlan.planName")
    @Mapping(target = "customerPlanId", source = "customerPlan.id")
    PaymentTransactionResponseDTO toResponseDTO(PaymentTransaction transaction);
}
