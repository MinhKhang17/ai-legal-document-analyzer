package com.analyzer.api.service.ai;

import com.analyzer.api.dto.ai.AiLegalQueryRequest;
import com.analyzer.api.dto.ai.AiLegalQueryResponse;

/**
 * Application service for legal AI queries.
 * The implementation should stay thin and delegate to the AI client.
 */
public interface AiLegalService {

    /**
     * Resolve a legal question and return the answer plus suggestion metadata.
     */
    AiLegalQueryResponse queryLegal(AiLegalQueryRequest request);
}
