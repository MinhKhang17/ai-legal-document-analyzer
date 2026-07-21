package com.analyzer.api.controller.knowledge;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.knowledge.*;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.enums.KnowledgeScope;
import com.analyzer.api.enums.KnowledgeStatus;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import com.analyzer.api.security.UserDetailsImpl;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.io.IOException;

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

    @PostMapping(value = "/{id}/source-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseVersionResponse>> uploadSourceFile(
            @PathVariable("id") String entryId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponseDTO.success("Luu file goc knowledge base thanh cong",
                knowledgeUploadService.storeSourceFile(entryId, file)));
    }

    @GetMapping("/{id}/source-file")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> downloadSourceFile(@PathVariable("id") String entryId) {
        KnowledgeBaseVersionResponse version = knowledgeUploadService.getCurrentVersion(entryId);
        Resource resource = knowledgeUploadService.loadSourceFile(entryId);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(version.getContentType()); }
        catch (Exception ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + version.getFileName().replace("\"", "") + "\"")
                .body(resource);
    }

    @PostMapping("/{id}/ingest")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeIngestionJobResponse>> ingestKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId,
            @Valid @RequestBody IngestKnowledgeRequest request,
            Authentication authentication) {
        Long adminId = authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails
                ? userDetails.getId() : null;
        return ResponseEntity.accepted()
                .body(ApiResponseDTO.accepted("Ingest knowledge base thanh cong",
                        knowledgeIngestionService.ingest(knowledgeBaseEntryId, request, adminId)));
    }

    @GetMapping("/ingestion-jobs/{jobId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeIngestionJobResponse>> getIngestionJob(
            @PathVariable String jobId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Lay trang thai ingest job thanh cong",
                knowledgeIngestionService.getJob(jobId)));
    }

    @PostMapping("/ingestion-jobs/{jobId}/failed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeIngestionJobResponse>> failIngestionJob(
            @PathVariable String jobId,
            @RequestBody KnowledgeIngestionProgressRequest request) {
        request.setStatus("FAILED");
        request.setProgressPercent(100);
        return ResponseEntity.ok(ApiResponseDTO.success("Danh dau ingest job that bai thanh cong",
                knowledgeIngestionService.updateProgress(jobId, request)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<PageResponse<KnowledgeBaseEntryResponse>>> listKnowledge(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(required = false) KnowledgeStatus status,
            @RequestParam(required = false) KnowledgeScope scope,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        Page<KnowledgeBaseEntryResponse> page = entryRepository.searchForAdmin(
                        normalizeFilter(keyword), status, scope, normalizeFilter(category), active, pageable)
                .map(this::toEntryResponse);
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

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<KnowledgeBaseVersionResponse>> unpublishKnowledge(
            @PathVariable("id") String knowledgeBaseEntryId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Unpublish knowledge base thanh cong",
                knowledgePublicationService.unpublish(knowledgeBaseEntryId)));
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
                .ingestStatus(version.getIngestStatus())
                .visibility(version.getVisibility())
                .active(version.getActive())
                .ingestedAt(version.getIngestedAt())
                .ingestedById(version.getIngestedBy() == null ? null : version.getIngestedBy().getId())
                .errorMessage(version.getErrorMessage())
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
                .description(version.getDescription())
                .fileName(version.getOriginalFileName())
                .contentType(version.getSourceContentType())
                .size(version.getSourceFileSize())
                .uploadedAt(version.getSourceUploadedAt())
                .sourceFileAvailable(version.getSourceStoragePath() != null)
                .sourceRelativePath(version.getSourceRelativePath())
                .sourceFileHash(version.getSourceFileHash())
                .ingestSource(version.getIngestSource())
                .neo4jDocumentId(version.getNeo4jDocumentId())
                .chunkCount(version.getChunkCount())
                .sourceVersionLabel(version.getSourceVersionLabel())
                .effectiveDate(version.getEffectiveDate())
                .build();
    }

    private Long toLongOrNull(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
