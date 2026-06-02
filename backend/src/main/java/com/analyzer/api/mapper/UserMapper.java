package com.analyzer.api.mapper;

import com.analyzer.api.domain.entity.User;
import com.analyzer.api.dto.UserRequestDTO;
import com.analyzer.api.dto.UserResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    User toEntity(UserRequestDTO requestDTO);

    UserResponseDTO toResponseDTO(User user);
}
