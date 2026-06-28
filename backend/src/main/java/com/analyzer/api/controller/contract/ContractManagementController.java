package com.analyzer.api.controller.contract;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.contract.*;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.contract.ContractGenerationService;
import com.analyzer.api.service.contract.ContractTemplateService;
import com.analyzer.api.service.contract.ContractVersionService;
import com.analyzer.api.service.contract.UserContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractManagementController {

    private final ContractTemplateService contractTemplateService;
    private final ContractGenerationService contractGenerationService;
    private final UserContractService userContractService;
    private final ContractVersionService contractVersionService;

    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<ContractTemplateResponse>> createTemplate(
            @Valid @RequestBody CreateContractTemplateRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponseDTO.created("Tao template hop dong thanh cong", contractTemplateService.create(request)));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<ContractTemplateResponse>> updateTemplate(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateContractTemplateRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Cap nhat template hop dong thanh cong",
                contractTemplateService.update(id, request)));
    }

    @GetMapping("/templates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<PageResponse<ContractTemplateResponse>>> getTemplates(Pageable pageable) {
        Page<ContractTemplateResponse> page = contractTemplateService.getAll(pageable);
        return ResponseEntity.ok(ApiResponseDTO.success("Lay danh sach template hop dong thanh cong", toPageResponse(page)));
    }

    @PostMapping("/generate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<ContractGenerationResponse>> generateContract(
            @Valid @RequestBody GenerateContractRequest request) {
        return ResponseEntity.status(202)
                .body(ApiResponseDTO.accepted("Tao job sinh hop dong thanh cong", contractGenerationService.generate(request)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<ContractResponse>> saveContract(
            @Valid @RequestBody SaveContractRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponseDTO.created("Luu hop dong thanh cong", userContractService.save(request)));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<PageResponse<ContractResponse>>> getMyContracts(Pageable pageable) {
        Page<ContractResponse> page = userContractService.getMyContracts(getCurrentUserId(), pageable);
        return ResponseEntity.ok(ApiResponseDTO.success("Lay danh sach hop dong cua toi thanh cong", toPageResponse(page)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<ContractResponse>> getContract(@PathVariable("id") String contractId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay thong tin hop dong thanh cong",
                userContractService.getById(getCurrentUserId(), contractId)));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<List<ContractVersionResponse>>> getVersions(@PathVariable("id") String contractId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay lich su phien ban hop dong thanh cong",
                contractVersionService.getVersions(contractId)));
    }

    @PostMapping("/{id}/versions/{versionNo}/revert")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<ContractResponse>> revertVersion(
            @PathVariable("id") String contractId,
            @PathVariable("versionNo") Integer versionNo,
            @Valid @RequestBody RevertContractVersionRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Khoi phuc phien ban hop dong thanh cong",
                contractVersionService.revert(contractId, versionNo, request)));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thong tin xac thuc khong hop le");
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
