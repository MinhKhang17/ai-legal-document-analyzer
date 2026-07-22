package com.analyzer.api.controller.document;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.document.DocumentResponse;
import com.analyzer.api.dto.document.ProcessingResultRequest;
import com.analyzer.api.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
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
    public ResponseEntity<ApiResponseDTO<DocumentResponse>> updateProcessingResult(
            @PathVariable String documentId, @Valid @RequestBody ProcessingResultRequest request) {
        DocumentResponse response = workspaceService.updateProcessingResult(documentId, request);
        return ResponseEntity.ok(ApiResponseDTO.success("Cập nhật kết quả xử lý document thành công", response));
    }

    @PostMapping("/register-generated")
    @Operation(summary = "Register generated document", description = "Endpoint used by Python AI Service to register generated contracts in PostgreSQL.")
    public ResponseEntity<ApiResponseDTO<DocumentResponse>> registerGeneratedDocument(
            @Valid @RequestBody com.analyzer.api.dto.document.RegisterDocumentRequest request) {
        DocumentResponse response = workspaceService.registerGeneratedDocument(request);
        return ResponseEntity.ok(ApiResponseDTO.success("Đăng ký document tạo tự động thành công", response));
    }
}
