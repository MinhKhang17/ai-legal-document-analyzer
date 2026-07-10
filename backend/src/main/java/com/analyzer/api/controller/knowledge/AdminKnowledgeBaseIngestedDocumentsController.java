package com.analyzer.api.controller.knowledge;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.knowledge.KnowledgeBaseIngestedDocumentResponse;
import com.analyzer.api.service.knowledge.KnowledgeIngestedDocumentsService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/knowledge-bases")
@RequiredArgsConstructor
public class AdminKnowledgeBaseIngestedDocumentsController {

    private final KnowledgeIngestedDocumentsService knowledgeIngestedDocumentsService;

    @GetMapping("/{kbId}/ingested-documents")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List ingested documents for a knowledge base")
    public ResponseEntity<ApiResponseDTO<PageResponse<KnowledgeBaseIngestedDocumentResponse>>> getIngestedDocuments(
            @PathVariable("kbId") String kbId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "ingestStatus", required = false) String ingestStatus,
            @RequestParam(value = "visibility", required = false) String visibility,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Lay danh sach tai lieu da ingest thanh cong",
                knowledgeIngestedDocumentsService.getIngestedDocuments(kbId, keyword, ingestStatus, visibility, page, size)));
    }
}
