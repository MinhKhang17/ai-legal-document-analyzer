package com.analyzer.api.controller;

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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspace Management", description = "APIs for creating workspaces and listing workspace documents")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create workspace", description = "Create a new workspace for the current authenticated user.")
    public ResponseEntity<WorkspaceResponseDTO> createWorkspace(
            @Valid @RequestBody WorkspaceRequestDTO request) {
        WorkspaceResponseDTO response = workspaceService.createWorkspace(getCurrentUserId(), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{workspaceId}/documents")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List workspace documents", description = "Get non-deleted documents in the workspace of the current authenticated user.")
    public ResponseEntity<List<DocumentResponseDTO>> getDocuments(@PathVariable String workspaceId) {
        List<DocumentResponseDTO> response = workspaceService.getDocuments(getCurrentUserId(), workspaceId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{workspaceId}/documents", consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload workspace document", description = "Upload a user document, save it, and request Python AI Service processing.")
    public ResponseEntity<DocumentResponseDTO> uploadDocument(
            @PathVariable String workspaceId,
            @RequestPart("file") MultipartFile file) throws IOException {
        DocumentResponseDTO response = workspaceService.uploadDocument(getCurrentUserId(), workspaceId, file);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
