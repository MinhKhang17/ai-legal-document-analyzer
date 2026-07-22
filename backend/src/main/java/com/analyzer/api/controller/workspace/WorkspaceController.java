package com.analyzer.api.controller.workspace;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.document.DocumentResponse;
import com.analyzer.api.dto.workspace.WorkspaceRequest;
import com.analyzer.api.dto.workspace.WorkspaceResponse;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping({"/api/v1/workspaces", "/api/workspaces"})
@RequiredArgsConstructor
@Tag(name = "Workspace Management", description = "APIs for managing user workspaces and workspace documents")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create workspace", description = "Create a new workspace for the current authenticated user.")
    public ResponseEntity<ApiResponseDTO<WorkspaceResponse>> createWorkspace(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody WorkspaceRequest request) {
        WorkspaceResponse response = workspaceService.createWorkspace(currentUser.getId(), request);
        return new ResponseEntity<>(
                ApiResponseDTO.created("Tạo workspace thành công", response),
                HttpStatus.CREATED
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List my workspaces", description = "Get all active workspaces of the current authenticated user.")
    public ResponseEntity<ApiResponseDTO<List<WorkspaceResponse>>> getMyWorkspaces(
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        List<WorkspaceResponse> response = workspaceService.getWorkspaces(currentUser.getId());
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách workspace thành công", response));
    }

    @GetMapping("/{workspaceId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get workspace detail", description = "Get a single active workspace by id for the current authenticated user.")
    public ResponseEntity<ApiResponseDTO<WorkspaceResponse>> getWorkspaceById(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable String workspaceId) {
        WorkspaceResponse response = workspaceService.getWorkspaceById(currentUser.getId(), workspaceId);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy thông tin workspace thành công", response));
    }

    @GetMapping("/{workspaceId}/documents")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List workspace documents", description = "Get non-deleted documents in the workspace of the current authenticated user.")
    public ResponseEntity<ApiResponseDTO<List<DocumentResponse>>> getDocuments(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable String workspaceId) {
        List<DocumentResponse> response = workspaceService.getDocuments(currentUser.getId(), workspaceId);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy danh sách document thành công", response));
    }

    @PostMapping("/{workspaceId}/documents")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Upload workspace document", description = "Upload a document and request AI processing. Contract type is detected internally when possible.")
    public ResponseEntity<ApiResponseDTO<DocumentResponse>> uploadDocument(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String workspaceId,
            @RequestPart("file") MultipartFile file) throws IOException {
        DocumentResponse response = workspaceService.uploadDocument(currentUser.getId(), workspaceId, file);
        return new ResponseEntity<>(
                ApiResponseDTO.accepted("Upload document thành công, đang gửi yêu cầu xử lý", response),
                HttpStatus.ACCEPTED
        );
    }

    @GetMapping("/{workspaceId}/documents/{documentId}/download")
    @Operation(summary = "Download workspace document", description = "Download a user document file by ID.")
    public ResponseEntity<org.springframework.core.io.Resource> downloadDocument(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String workspaceId,
            @PathVariable String documentId) throws java.io.IOException {
        Long userId = currentUser == null ? null : currentUser.getId();

        org.springframework.core.io.Resource resource;
        if (userId != null) {
            resource = workspaceService.downloadDocumentFile(userId, workspaceId, documentId);
        } else {
            resource = workspaceService.downloadDocumentFilePublic(workspaceId, documentId);
        }

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{workspaceId}/documents/{documentId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Soft delete workspace document", description = "Soft delete a document (status set to DELETED).")
    public ResponseEntity<ApiResponseDTO<Void>> deleteDocument(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String workspaceId,
            @PathVariable String documentId) {
        workspaceService.softDeleteDocument(currentUser.getId(), workspaceId, documentId);
        return ResponseEntity.ok(ApiResponseDTO.success("Xóa tài liệu thành công", null));
    }

    @GetMapping("/{workspaceId}/documents/system/download")
    @Operation(summary = "Download system knowledge base document", description = "Download a system knowledge base document by filename.")
    public ResponseEntity<org.springframework.core.io.Resource> downloadSystemDocument(
            @PathVariable String workspaceId,
            @RequestParam String filename) {
        org.springframework.core.io.Resource resource = workspaceService.downloadSystemDocumentFile(filename);
        
        String realFilename = (resource != null && resource.getFilename() != null) ? resource.getFilename() : filename;
        String lowerName = realFilename.toLowerCase();

        String contentType = "application/octet-stream";
        String downloadName = realFilename;

        if (lowerName.endsWith(".docx")) {
            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lowerName.endsWith(".doc")) {
            contentType = "application/msword";
        } else if (lowerName.endsWith(".pdf")) {
            contentType = "application/pdf";
        } else if (lowerName.endsWith(".txt")) {
            contentType = "text/plain; charset=utf-8";
        } else if (lowerName.endsWith(".md")) {
            contentType = "text/plain; charset=utf-8";
            downloadName = realFilename + ".txt";
        } else {
            contentType = "text/plain; charset=utf-8";
            downloadName = realFilename + ".txt";
        }

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .header(org.springframework.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition")
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
