package com.analyzer.api.mapper;

import com.analyzer.api.dto.ai.AiCitationResponse;
import com.analyzer.api.entity.AiCitation;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AiFeatureMapper {

    AiCitationResponse toResponse(AiCitation citation);
}
