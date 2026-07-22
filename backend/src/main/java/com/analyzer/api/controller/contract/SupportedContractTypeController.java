package com.analyzer.api.controller.contract;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.enums.SupportedContractType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "Supported contract scope", description = "Canonical simple personal contract types supported by analysis")
public class SupportedContractTypeController {

    @GetMapping("/supported-types")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List supported contract types")
    public ResponseEntity<ApiResponseDTO<List<SupportedTypeResponse>>> list() {
        List<SupportedTypeResponse> types = Arrays.stream(SupportedContractType.values())
                .filter(SupportedContractType::isSupported)
                .map(type -> new SupportedTypeResponse(type.name(), type.getDisplayName()))
                .toList();
        return ResponseEntity.ok(ApiResponseDTO.success("Supported contract types retrieved successfully", types));
    }

    public record SupportedTypeResponse(String value, String displayName) {}
}
