package com.analyzer.api.controller.admin;

import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/documents")
@RequiredArgsConstructor
@Tag(name = "Admin Document Management", description = "Endpoints for admin/expert to download original uploaded documents")
public class AdminDocumentController {

    private final WorkspaceService workspaceService;

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXPERT')")
    @Operation(summary = "Download original document", description = "Admin can download any document; Expert can only download documents attached to a ticket assigned to them.")
    public ResponseEntity<Resource> downloadDocument(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable String documentId) {
        Resource resource = workspaceService.downloadDocumentForStaff(currentUser.getId(), currentUser.getRoleName(), documentId);
        String filename = resource.getFilename() == null ? documentId : resource.getFilename();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
