package com.analyzer.api.mapper;

import com.analyzer.api.domain.entity.User;
import com.analyzer.api.dto.UserRequestDTO;
import com.analyzer.api.dto.UserResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "active", ignore = true)
    User toEntity(UserRequestDTO requestDTO);

    @Mapping(target = "role", source = "role.name")
    UserResponseDTO toResponseDTO(User user);
}