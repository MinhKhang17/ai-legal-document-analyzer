package com.analyzer.api.service;

import com.analyzer.api.dto.ai.AiLegalQueryRequest;
import com.analyzer.api.dto.ai.AiLegalQueryResponse;
import com.analyzer.api.dto.ai.GenerateContractApiRequest;
import com.analyzer.api.dto.ai.GenerateContractApiResponse;

/**
 * Contract for the BE-side AI client abstraction.
 * This keeps the Python transport swappable without touching service logic.
 */
public interface AiClient {

    /**
     * Send a legal query to the AI service and receive the answer plus ticket metadata.
     */
    AiLegalQueryResponse queryLegal(AiLegalQueryRequest request);

    /**
     * Call Python AI Service to generate contract content using LLM.
     */
    GenerateContractApiResponse generateContract(GenerateContractApiRequest request);
}
