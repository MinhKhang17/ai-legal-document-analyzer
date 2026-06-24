package com.analyzer.api.service;

import com.analyzer.api.dto.ai.AiLegalQueryRequest;
import com.analyzer.api.dto.ai.AiLegalQueryResponse;

/**
 * Contract for the BE-side AI client abstraction.
 * This keeps the Python transport swappable without touching service logic.
 */
public interface AiClient {

    /**
     * Send a legal query to the AI service and receive the answer plus ticket metadata.
     */
    AiLegalQueryResponse queryLegal(AiLegalQueryRequest request);
}
