package com.analyzer.api.mapper;

import com.analyzer.api.dto.user.UserRequest;
import com.analyzer.api.dto.user.UserResponse;
import com.analyzer.api.entity.User;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "emailVerificationToken", ignore = true)
    @Mapping(target = "emailVerificationLastUsedToken", ignore = true)
    @Mapping(target = "emailVerificationTokenUsedAt", ignore = true)
    @Mapping(target = "emailVerificationTokenExpiry", ignore = true)
    @Mapping(target = "emailVerificationRequestedAt", ignore = true)
    @Mapping(target = "emailVerifiedAt", ignore = true)
    @Mapping(target = "emailDeliveryStatus", ignore = true)
    @Mapping(target = "specialty", ignore = true)
    @Mapping(target = "legalDomain", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "mustChangePassword", ignore = true)
    @Mapping(target = "passwordResetDeadline", ignore = true)
    @Mapping(target = "forgotPasswordToken", ignore = true)
    @Mapping(target = "forgotPasswordTokenExpiry", ignore = true)
    @Mapping(target = "forgotPasswordRequestedAt", ignore = true)
    User toEntity(UserRequest requestDTO);

    @Mapping(target = "role", source = "role.name")
    UserResponse toResponseDTO(User user);
}
