package com.analyzer.api.service.impl;

import com.analyzer.api.dto.ai.AiLegalQueryRequest;
import com.analyzer.api.dto.ai.AiLegalQueryResponse;
import com.analyzer.api.service.AiClient;
import com.analyzer.api.service.AiLegalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Thin application service for the legal AI query endpoint.
 */
@Service
@RequiredArgsConstructor
public class AiLegalServiceImpl implements AiLegalService {

    private final AiClient aiClient;

    @Override
    public AiLegalQueryResponse queryLegal(AiLegalQueryRequest request) {
        return aiClient.queryLegal(request);
    }
}
