package com.analyzer.api.controller.contract;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.contract.ContractGenerationResponse;
import com.analyzer.api.dto.contract.ContractResponse;
import com.analyzer.api.dto.contract.ContractTemplateResponse;
import com.analyzer.api.dto.contract.ContractVersionResponse;
import com.analyzer.api.dto.contract.CreateContractTemplateRequest;
import com.analyzer.api.dto.contract.GenerateContractRequest;
import com.analyzer.api.dto.contract.RevertContractVersionRequest;
import com.analyzer.api.dto.contract.SaveContractRequest;
import com.analyzer.api.dto.contract.UpdateContractTemplateRequest;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.contract.ContractGenerationService;
import com.analyzer.api.service.contract.ContractTemplateService;
import com.analyzer.api.service.contract.ContractVersionService;
import com.analyzer.api.service.contract.UserContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        ContractTemplateResponse response = contractTemplateService.create(request);
        return new ResponseEntity<>(
                ApiResponseDTO.created("Tạo template thành công", response),
                HttpStatus.CREATED
        );
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<ContractTemplateResponse>> updateTemplate(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateContractTemplateRequest request) {
        ContractTemplateResponse response = contractTemplateService.update(id, request);
        return ResponseEntity.ok(ApiResponseDTO.success("Cập nhật template thành công", response));
    }

    @GetMapping("/templates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<PageResponse<ContractTemplateResponse>>> getTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ContractTemplateResponse> pageResult = contractTemplateService.getAll(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách template thành công", toPageResponse(pageResult)));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponseDTO<ContractGenerationResponse>> generateContract(
            @Valid @RequestBody GenerateContractRequest request) {
        ContractGenerationResponse response = contractGenerationService.generate(request);
        return ResponseEntity.ok(ApiResponseDTO.success("Tạo hợp đồng thành công", response));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDTO<ContractResponse>> saveContract(
            @Valid @RequestBody SaveContractRequest request) {
        ContractResponse response = userContractService.save(request);
        return new ResponseEntity<>(
                ApiResponseDTO.created("Lưu hợp đồng thành công", response),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponseDTO<PageResponse<ContractResponse>>> getMyContracts(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ContractResponse> pageResult = userContractService.getMyContracts(
                currentUser.getId(),
                PageRequest.of(page, size)
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách hợp đồng thành công", toPageResponse(pageResult)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponseDTO<ContractResponse>> getContract(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable("id") String contractId) {
        ContractResponse response = userContractService.getById(currentUser.getId(), contractId);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy thông tin hợp đồng thành công", response));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponseDTO<List<ContractVersionResponse>>> getVersions(@PathVariable("id") String contractId) {
        List<ContractVersionResponse> response = contractVersionService.getVersions(contractId);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách phiên bản thành công", response));
    }

    @PostMapping("/{id}/versions/{versionNo}/revert")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponseDTO<ContractResponse>> revertVersion(
            @PathVariable("id") String contractId,
            @PathVariable("versionNo") Integer versionNo,
            @Valid @RequestBody RevertContractVersionRequest request) {
        ContractResponse response = contractVersionService.revert(contractId, versionNo, request);
        return ResponseEntity.ok(ApiResponseDTO.success("Khôi phục phiên bản thành công", response));
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
