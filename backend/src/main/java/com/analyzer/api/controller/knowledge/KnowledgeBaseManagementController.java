package com.analyzer.api.controller.knowledge;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.knowledge.*;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.knowledge.KnowledgeArchiveService;
import com.analyzer.api.service.knowledge.KnowledgeIngestionService;
import com.analyzer.api.service.knowledge.KnowledgePublicationService;
import com.analyzer.api.service.knowledge.KnowledgeReviewService;
import com.analyzer.api.service.knowledge.KnowledgeUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseManagementController {

    private final KnowledgeUploadService knowledgeUploadService;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final KnowledgeReviewService knowledgeReviewService;
    private final KnowledgePublicationService knowledgePublicationService;
    private final KnowledgeArchiveService knowledgeArchiveService;
    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseVersionResponse>> uploadKnowledge(
            @Valid @RequestBody UploadKnowledgeRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponseDTO.created("Upload knowledge base thanh cong", knowledgeUploadService.upload(request)));
    }

    @PostMapping("/{id}/ingest")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeIngestionJobResponse>> ingestKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId,
            @Valid @RequestBody IngestKnowledgeRequest request) {
        return ResponseEntity.accepted()
                .body(ApiResponseDTO.accepted("Ingest knowledge base thanh cong",
                        knowledgeIngestionService.ingest(knowledgeBaseEntryId, request)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<PageResponse<KnowledgeBaseEntryResponse>>> listKnowledge(Pageable pageable) {
        Page<KnowledgeBaseEntryResponse> page = entryRepository.findAll(pageable).map(this::toEntryResponse);
        return ResponseEntity.ok(ApiResponseDTO.success("Lay danh sach knowledge base thanh cong", toPageResponse(page)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseEntryResponse>> getKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId) {
        KnowledgeBaseEntry entry = entryRepository.findById(knowledgeBaseEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay knowledge base ID: " + knowledgeBaseEntryId));
        return ResponseEntity.ok(ApiResponseDTO.success("Lay knowledge base thanh cong", toEntryResponse(entry)));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<List<KnowledgeBaseVersionResponse>>> getVersions(
            @PathVariable("id") String knowledgeBaseEntryId) {
        entryRepository.findById(knowledgeBaseEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay knowledge base ID: " + knowledgeBaseEntryId));
        List<KnowledgeBaseVersionResponse> versions = versionRepository
                .findByKnowledgeBaseEntryIdOrderByVersionNoDesc(knowledgeBaseEntryId)
                .stream()
                .map(this::toVersionResponse)
                .toList();
        return ResponseEntity.ok(ApiResponseDTO.success("Lay danh sach version knowledge base thanh cong", versions));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseVersionResponse>> reviewKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId,
            @Valid @RequestBody KnowledgeReviewRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Review knowledge base thanh cong",
                knowledgeReviewService.review(knowledgeBaseEntryId, request)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseVersionResponse>> publishKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId,
            @Valid @RequestBody PublishKnowledgeRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Publish knowledge base thanh cong",
                knowledgePublicationService.publish(knowledgeBaseEntryId, request)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseVersionResponse>> archiveKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId,
            @Valid @RequestBody ArchiveKnowledgeRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Archive knowledge base thanh cong",
                knowledgeArchiveService.archive(knowledgeBaseEntryId, request)));
    }

    private KnowledgeBaseEntryResponse toEntryResponse(KnowledgeBaseEntry entry) {
        return KnowledgeBaseEntryResponse.builder()
                .id(entry.getId())
                .code(entry.getCode())
                .title(entry.getTitle())
                .category(entry.getCategory())
                .scope(entry.getScope())
                .currentVersionNo(entry.getCurrentVersionNo())
                .currentStatus(entry.getCurrentStatus())
                .active(entry.getActive())
                .createdById(entry.getCreatedBy().getId())
                .workspaceId(toLongOrNull(entry.getWorkspace() == null ? null : entry.getWorkspace().getId()))
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }

    private KnowledgeBaseVersionResponse toVersionResponse(KnowledgeBaseVersion version) {
        return KnowledgeBaseVersionResponse.builder()
                .id(version.getId())
                .knowledgeBaseEntryId(version.getKnowledgeBaseEntry().getId())
                .versionNo(version.getVersionNo())
                .sourceDocumentId(version.getSourceDocument() == null ? null : version.getSourceDocument().getId())
                .rawContent(version.getRawContent())
                .extractedContent(version.getExtractedContent())
                .status(version.getStatus())
                .reviewDecision(version.getReviewDecision())
                .reviewedById(version.getReviewedBy() == null ? null : version.getReviewedBy().getId())
                .reviewedAt(version.getReviewedAt())
                .publishedById(version.getPublishedBy() == null ? null : version.getPublishedBy().getId())
                .publishedAt(version.getPublishedAt())
                .archivedById(version.getArchivedBy() == null ? null : version.getArchivedBy().getId())
                .archivedAt(version.getArchivedAt())
                .failedReason(version.getFailedReason())
                .createdAt(version.getCreatedAt())
                .updatedAt(version.getUpdatedAt())
                .build();
    }

    private Long toLongOrNull(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
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
