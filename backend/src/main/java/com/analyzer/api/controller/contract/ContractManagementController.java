package com.analyzer.api.controller.contract;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.contract.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
public class ContractManagementController {

    @PostMapping("/templates")
    public ResponseEntity<ApiResponseDTO<ContractTemplateResponse>> createTemplate(
            @Valid @RequestBody CreateContractTemplateRequest request) {
        return notImplemented();
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<ApiResponseDTO<ContractTemplateResponse>> updateTemplate(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateContractTemplateRequest request) {
        return notImplemented();
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponseDTO<PageResponse<ContractTemplateResponse>>> getTemplates() {
        return notImplemented();
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponseDTO<ContractGenerationResponse>> generateContract(
            @Valid @RequestBody GenerateContractRequest request) {
        return notImplemented();
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponseDTO<PageResponse<ContractResponse>>> getMyContracts() {
        return notImplemented();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<ContractResponse>> getContract(@PathVariable("id") String contractId) {
        return notImplemented();
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponseDTO<List<ContractVersionResponse>>> getVersions(@PathVariable("id") String contractId) {
        return notImplemented();
    }

    @PostMapping("/{id}/versions/{versionNo}/revert")
    public ResponseEntity<ApiResponseDTO<ContractResponse>> revertVersion(
            @PathVariable("id") String contractId,
            @PathVariable("versionNo") Integer versionNo,
            @Valid @RequestBody RevertContractVersionRequest request) {
        return notImplemented();
    }

    private <T> ResponseEntity<ApiResponseDTO<T>> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponseDTO.error(501, "Phase 2 contract only", null));
    }
}
