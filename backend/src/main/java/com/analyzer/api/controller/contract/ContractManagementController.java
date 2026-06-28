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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<ApiResponseDTO<PageResponse<ContractTemplateResponse>>> getTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ContractTemplateResponse> pageResult = contractTemplateService.getAll(PageRequest.of(page, size));
        PageResponse<ContractTemplateResponse> response = PageResponse.<ContractTemplateResponse>builder()
                .items(pageResult.getContent())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalItems(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách template thành công", response));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponseDTO<ContractGenerationResponse>> generateContract(
            @Valid @RequestBody GenerateContractRequest request) {
        ContractGenerationResponse response = contractGenerationService.generate(request);
        return ResponseEntity.ok(ApiResponseDTO.success("Tạo hợp đồng thành công", response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponseDTO<PageResponse<ContractResponse>>> getMyContracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ContractResponse> pageResult = userContractService.getMyContracts(getCurrentUserId(), PageRequest.of(page, size));
        PageResponse<ContractResponse> response = PageResponse.<ContractResponse>builder()
                .items(pageResult.getContent())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalItems(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách hợp đồng thành công", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponseDTO<ContractResponse>> getContract(@PathVariable("id") String contractId) {
        ContractResponse response = userContractService.getById(getCurrentUserId(), contractId);
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

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }

        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
