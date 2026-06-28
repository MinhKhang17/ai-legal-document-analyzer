package com.analyzer.api.service.ai.impl;

import com.analyzer.api.dto.ai.AiCitationResponse;
import com.analyzer.api.entity.AiCitation;
import com.analyzer.api.mapper.AiFeatureMapper;
import com.analyzer.api.repository.ai.AiCitationRepository;
import com.analyzer.api.service.ai.AiCitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiCitationServiceImpl implements AiCitationService {

    private final AiCitationRepository aiCitationRepository;
    private final AiFeatureMapper aiFeatureMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AiCitationResponse> getTicketCitations(String ticketId) {
        List<AiCitation> citations = aiCitationRepository.findByLegalTicket_Id(ticketId);
        return citations.stream()
                .map(aiFeatureMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiCitationResponse> getChatMessageCitations(String chatMessageId) {
        List<AiCitation> citations = aiCitationRepository.findByChatMessage_Id(chatMessageId);
        return citations.stream()
                .map(aiFeatureMapper::toResponse)
                .toList();
    }
}
