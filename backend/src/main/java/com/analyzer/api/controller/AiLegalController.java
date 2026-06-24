package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.ai.AiLegalQueryRequest;
import com.analyzer.api.dto.ai.AiLegalQueryResponse;
import com.analyzer.api.service.AiLegalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Thin controller for legal AI queries.
 * The endpoint exists so the FE can request the answer and ticket-suggestion metadata in one call.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "AI Legal Query", description = "AI legal query endpoint with lawyer-ticket suggestion metadata")
public class AiLegalController {

    private final AiLegalService aiLegalService;

    @PostMapping("/api/ai/legal-query")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Run a legal AI query", description = "Returns an AI answer plus ticket-suggestion metadata.")
    public ResponseEntity<ApiResponseDTO<AiLegalQueryResponse>> legalQuery(@Valid @RequestBody AiLegalQueryRequest request) {
        AiLegalQueryResponse response = aiLegalService.queryLegal(request);
        return ResponseEntity.ok(ApiResponseDTO.success("Legal query processed successfully", response));
    }
}
