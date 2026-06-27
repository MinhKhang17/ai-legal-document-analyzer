package com.analyzer.api.controller.knowledge;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.knowledge.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/knowledge-base")
public class KnowledgeBaseManagementController {

    @PostMapping("/upload")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseVersionResponse>> uploadKnowledge(
            @Valid @RequestBody UploadKnowledgeRequest request) {
        return notImplemented();
    }

    @PostMapping("/{id}/ingest")
    public ResponseEntity<ApiResponseDTO<KnowledgeIngestionJobResponse>> ingestKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId,
            @Valid @RequestBody IngestKnowledgeRequest request) {
        return notImplemented();
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<PageResponse<KnowledgeBaseEntryResponse>>> listKnowledge() {
        return notImplemented();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseEntryResponse>> getKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId) {
        return notImplemented();
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponseDTO<List<KnowledgeBaseVersionResponse>>> getVersions(
            @PathVariable("id") String knowledgeBaseEntryId) {
        return notImplemented();
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseVersionResponse>> reviewKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId,
            @Valid @RequestBody KnowledgeReviewRequest request) {
        return notImplemented();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseVersionResponse>> publishKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId,
            @Valid @RequestBody PublishKnowledgeRequest request) {
        return notImplemented();
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseVersionResponse>> archiveKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId,
            @Valid @RequestBody ArchiveKnowledgeRequest request) {
        return notImplemented();
    }

    private <T> ResponseEntity<ApiResponseDTO<T>> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponseDTO.error(501, "Phase 2 contract only", null));
    }
}
