package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.document.DocumentResponseDTO;
import com.analyzer.api.dto.workspace.WorkspaceRequestDTO;
import com.analyzer.api.dto.workspace.WorkspaceResponseDTO;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspace Management", description = "APIs for managing user workspaces and workspace documents")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create workspace", description = "Create a new workspace for the current authenticated user.")
    public ResponseEntity<ApiResponseDTO<WorkspaceResponseDTO>> createWorkspace(
            @Valid @RequestBody WorkspaceRequestDTO request) {
        WorkspaceResponseDTO response = workspaceService.createWorkspace(getCurrentUserId(), request);
        return new ResponseEntity<>(
                ApiResponseDTO.created("Tạo workspace thành công", response),
                HttpStatus.CREATED
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List my workspaces", description = "Get all active workspaces of the current authenticated user.")
    public ResponseEntity<ApiResponseDTO<List<WorkspaceResponseDTO>>> getMyWorkspaces() {
        List<WorkspaceResponseDTO> response = workspaceService.getWorkspaces(getCurrentUserId());
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách workspace thành công", response));
    }

    @GetMapping("/{workspaceId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get workspace detail", description = "Get a single active workspace by id for the current authenticated user.")
    public ResponseEntity<ApiResponseDTO<WorkspaceResponseDTO>> getWorkspaceById(@PathVariable String workspaceId) {
        WorkspaceResponseDTO response = workspaceService.getWorkspaceById(getCurrentUserId(), workspaceId);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy thông tin workspace thành công", response));
    }

    @GetMapping("/{workspaceId}/documents")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List workspace documents", description = "Get non-deleted documents in the workspace of the current authenticated user.")
    public ResponseEntity<ApiResponseDTO<List<DocumentResponseDTO>>> getDocuments(@PathVariable String workspaceId) {
        List<DocumentResponseDTO> response = workspaceService.getDocuments(getCurrentUserId(), workspaceId);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách document thành công", response));
    }

    @PostMapping(value = "/{workspaceId}/documents", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Upload workspace document", description = "Upload a user document, save it, and request Python AI Service processing.")
    public ResponseEntity<ApiResponseDTO<DocumentResponseDTO>> uploadDocument(
            @PathVariable String workspaceId,
            @RequestPart("file") MultipartFile file) throws IOException {
        DocumentResponseDTO response = workspaceService.uploadDocument(getCurrentUserId(), workspaceId, file);
        return new ResponseEntity<>(
                ApiResponseDTO.accepted("Upload document thành công, đang gửi yêu cầu xử lý", response),
                HttpStatus.ACCEPTED
        );
    }

    @GetMapping("/{workspaceId}/documents/{documentId}/download")
    @Operation(summary = "Download workspace document", description = "Download a user document file by ID.")
    public ResponseEntity<org.springframework.core.io.Resource> downloadDocument(
            @PathVariable String workspaceId,
            @PathVariable String documentId) throws java.io.IOException {
        org.springframework.core.io.Resource resource = workspaceService.downloadDocumentFilePublic(workspaceId, documentId);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/{workspaceId}/documents/system/download")
    @Operation(summary = "Download system knowledge base document", description = "Download a system knowledge base document by filename.")
    public ResponseEntity<org.springframework.core.io.Resource> downloadSystemDocument(
            @PathVariable String workspaceId,
            @RequestParam String filename) {
        org.springframework.core.io.Resource resource = workspaceService.downloadSystemDocumentFile(filename);
        
        String contentType = "application/octet-stream";
        if (filename.endsWith(".docx")) {
            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (filename.endsWith(".doc")) {
            contentType = "application/msword";
        } else if (filename.endsWith(".pdf")) {
            contentType = "application/pdf";
        } else if (filename.endsWith(".txt")) {
            contentType = "text/plain";
        }

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .body(resource);
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
