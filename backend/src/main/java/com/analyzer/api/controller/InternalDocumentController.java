package com.analyzer.api.controller;

import com.analyzer.api.dto.document.DocumentResponseDTO;
import com.analyzer.api.dto.document.ProcessingResultRequestDTO;
import com.analyzer.api.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/documents")
@RequiredArgsConstructor
@Tag(name = "Internal Document Processing", description = "Internal APIs used by Python AI Service")
public class InternalDocumentController {

    private final WorkspaceService workspaceService;

    @PostMapping("/{documentId}/processing-result")
    @Operation(summary = "Receive document processing result", description = "Callback endpoint used by Python AI Service.")
    public ResponseEntity<DocumentResponseDTO> updateProcessingResult(
            @PathVariable String documentId,
            @RequestBody ProcessingResultRequestDTO request) {
        DocumentResponseDTO response = workspaceService.updateProcessingResult(documentId, request);
        return ResponseEntity.ok(response);
    }
}
