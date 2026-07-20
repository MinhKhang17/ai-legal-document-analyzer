package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.knowledge.KnowledgeBaseIngestedDocumentResponse;
import com.analyzer.api.dto.knowledge.KnowledgeBaseIngestedDocumentVersionResponse;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.knowledge.KnowledgeIngestedDocumentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeIngestedDocumentsServiceImpl implements KnowledgeIngestedDocumentsService {

    private static final int AI_SNAPSHOT_PAGE_SIZE = 100;

    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;
    private final KnowledgeBaseAiClient aiClient;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeBaseIngestedDocumentResponse> getIngestedDocuments(
            String knowledgeBaseEntryId,
            String keyword,
            String ingestStatus,
            String visibility,
            int page,
            int size) {
        KnowledgeBaseEntry entry = entryRepository.findById(knowledgeBaseEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay knowledge base ID: " + knowledgeBaseEntryId));

        List<KnowledgeBaseVersion> versions = versionRepository.findByKnowledgeBaseEntryIdOrderByVersionNoDesc(knowledgeBaseEntryId);
        Map<String, AiDocumentSnapshot> aiSnapshots = loadAiSnapshots(knowledgeBaseEntryId, keyword, ingestStatus, visibility);

        List<KnowledgeBaseVersion> orderedVersions = versions.stream()
                .sorted(Comparator.comparing(KnowledgeBaseVersion::getVersionNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        List<KnowledgeBaseIngestedDocumentVersionResponse> enrichedVersions = new ArrayList<>(orderedVersions.size());
        for (int index = 0; index < orderedVersions.size(); index++) {
            KnowledgeBaseVersion version = orderedVersions.get(index);
            KnowledgeBaseIngestedDocumentVersionResponse response = toVersionResponse(version, aiSnapshots);
            if (index + 1 < orderedVersions.size()) {
                response.setEffectiveTo(orderedVersions.get(index + 1).getCreatedAt());
            }
            if (matchesFilters(response, keyword, ingestStatus, visibility)) {
                enrichedVersions.add(response);
            }
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int totalItems = enrichedVersions.size();
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalItems);
        int toIndex = Math.min(fromIndex + safeSize, totalItems);
        List<KnowledgeBaseIngestedDocumentVersionResponse> pagedVersions =
                fromIndex >= toIndex ? List.of() : enrichedVersions.subList(fromIndex, toIndex);

        KnowledgeBaseIngestedDocumentResponse documentResponse = KnowledgeBaseIngestedDocumentResponse.builder()
                .legalDocumentId(entry.getId())
                .title(entry.getTitle())
                .documentCode(entry.getCode())
                .versions(pagedVersions)
                .build();

        return PageResponse.<KnowledgeBaseIngestedDocumentResponse>builder()
                .items(pagedVersions.isEmpty() ? List.of() : List.of(documentResponse))
                .page(safePage)
                .size(safeSize)
                .totalItems(totalItems)
                .totalPages(totalPages)
                .build();
    }

    private Map<String, AiDocumentSnapshot> loadAiSnapshots(
            String knowledgeBaseEntryId,
            String keyword,
            String ingestStatus,
            String visibility) {
        Map<String, AiDocumentSnapshot> snapshots = new HashMap<>();
        int snapshotPageNumber = 0;
        int totalPages;
        do {
            PageResponse<KnowledgeBaseIngestedDocumentResponse> snapshotPage = aiClient.getIngestedDocuments(
                    knowledgeBaseEntryId,
                    keyword,
                    ingestStatus,
                    visibility,
                    snapshotPageNumber,
                    AI_SNAPSHOT_PAGE_SIZE);
            if (snapshotPage.getItems() != null) {
                for (KnowledgeBaseIngestedDocumentResponse document : snapshotPage.getItems()) {
                    if (document.getVersions() == null) {
                        continue;
                    }
                    for (KnowledgeBaseIngestedDocumentVersionResponse version : document.getVersions()) {
                        if (StringUtils.hasText(version.getSourceFileId())) {
                            snapshots.put(version.getSourceFileId(), new AiDocumentSnapshot(document, version));
                        }
                    }
                }
            }
            totalPages = Math.max(snapshotPage.getTotalPages(), 0);
            snapshotPageNumber++;
            if (totalPages == 0) {
                break;
            }
        } while (snapshotPageNumber < totalPages);

        return snapshots;
    }

    private KnowledgeBaseIngestedDocumentVersionResponse toVersionResponse(
            KnowledgeBaseVersion version,
            Map<String, AiDocumentSnapshot> aiSnapshots) {
        Document sourceDocument = version.getSourceDocument();
        String sourceFileId = sourceDocument != null ? sourceDocument.getId() : null;
        String aiDocumentId = StringUtils.hasText(version.getNeo4jDocumentId())
                ? version.getNeo4jDocumentId()
                : sourceFileId;
        AiDocumentSnapshot aiSnapshot = aiDocumentId == null ? null : aiSnapshots.get(aiDocumentId);
        Integer chunkCount = version.getChunkCount() != null
                ? version.getChunkCount()
                : sourceDocument != null && sourceDocument.getChunkCount() != null
                ? sourceDocument.getChunkCount()
                : aiSnapshot != null && aiSnapshot.version().getChunkCount() != null
                    ? aiSnapshot.version().getChunkCount()
                    : 0;
        Integer embeddedCount = aiSnapshot != null && aiSnapshot.version().getEmbeddedCount() != null
                ? aiSnapshot.version().getEmbeddedCount()
                : chunkCount;

        LocalDateTime ingestedAt = version.getIngestedAt() != null
                ? version.getIngestedAt()
                : sourceDocument != null && sourceDocument.getProcessedAt() != null
                ? sourceDocument.getProcessedAt()
                : version.getCreatedAt();

        String visibility = version.getVisibility() != null
                ? version.getVisibility().name()
                : sourceDocument != null && sourceDocument.getVisibilityScope() != null
                ? sourceDocument.getVisibilityScope().name()
                : null;

        return KnowledgeBaseIngestedDocumentVersionResponse.builder()
                .versionId(version.getId())
                .versionLabel("v" + version.getVersionNo())
                .effectiveFrom(version.getCreatedAt())
                .effectiveTo(null)
                .visibility(visibility)
                .active(version.getActive())
                .ingestStatus(resolveIngestStatus(version, aiSnapshot))
                .chunkCount(chunkCount)
                .embeddedCount(embeddedCount)
                .sourceFileId(aiDocumentId)
                .contentHash(version.getSourceFileHash() != null ? version.getSourceFileHash()
                        : aiSnapshot != null ? aiSnapshot.version().getContentHash() : null)
                .ingestedAt(ingestedAt)
                .publishedAt(version.getPublishedAt())
                .ingestedById(version.getIngestedBy() == null ? null : version.getIngestedBy().getId())
                .errorMessage(version.getErrorMessage() != null ? version.getErrorMessage() : version.getFailedReason())
                .originalFileName(version.getOriginalFileName())
                .ingestSource(version.getIngestSource())
                .createdAt(version.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeBaseIngestedDocumentResponse> searchIngestedDocuments(
            String title, String category, Integer versionNo, String source, String status,
            LocalDateTime createdFrom, LocalDateTime createdTo, int page, int size) {
        List<KnowledgeBaseIngestedDocumentResponse> matches = versionRepository.findAll().stream()
                .filter(version -> matchesGlobalFilters(version, title, category, versionNo, source, status,
                        createdFrom, createdTo))
                .sorted(Comparator.comparing(KnowledgeBaseVersion::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(version -> KnowledgeBaseIngestedDocumentResponse.builder()
                        .legalDocumentId(version.getKnowledgeBaseEntry().getId())
                        .title(version.getKnowledgeBaseEntry().getTitle())
                        .documentCode(version.getKnowledgeBaseEntry().getCode())
                        .versions(List.of(toVersionResponse(version, Map.of())))
                        .build())
                .toList();
        int totalItems = matches.size();
        int fromIndex = Math.min(page * size, totalItems);
        int toIndex = Math.min(fromIndex + size, totalItems);
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        return PageResponse.<KnowledgeBaseIngestedDocumentResponse>builder()
                .items(fromIndex >= toIndex ? List.of() : matches.subList(fromIndex, toIndex))
                .page(page).size(size).totalItems(totalItems).totalPages(totalPages).build();
    }

    private boolean matchesGlobalFilters(KnowledgeBaseVersion version, String title, String category,
                                         Integer versionNo, String source, String status,
                                         LocalDateTime createdFrom, LocalDateTime createdTo) {
        KnowledgeBaseEntry entry = version.getKnowledgeBaseEntry();
        if (StringUtils.hasText(title) && !contains(entry.getTitle(), title.trim().toLowerCase(Locale.ROOT))) return false;
        if (StringUtils.hasText(category) && !category.trim().equalsIgnoreCase(entry.getCategory())) return false;
        if (versionNo != null && !versionNo.equals(version.getVersionNo())) return false;
        if (StringUtils.hasText(status)) {
            String actual = version.getIngestStatus() != null ? version.getIngestStatus().name() : version.getStatus().name();
            if (!status.trim().equalsIgnoreCase(actual)) return false;
        }
        if (StringUtils.hasText(source)) {
            String keyword = source.trim().toLowerCase(Locale.ROOT);
            if (!contains(version.getOriginalFileName(), keyword)
                    && !contains(version.getIngestSource(), keyword)
                    && !contains(version.getSourceRelativePath(), keyword)) return false;
        }
        if (createdFrom != null && (version.getCreatedAt() == null || version.getCreatedAt().isBefore(createdFrom))) return false;
        return createdTo == null || (version.getCreatedAt() != null && !version.getCreatedAt().isAfter(createdTo));
    }

    private String resolveIngestStatus(KnowledgeBaseVersion version, AiDocumentSnapshot aiSnapshot) {
        if (version.getIngestStatus() != null) {
            return version.getIngestStatus().name();
        }
        return aiSnapshot != null ? aiSnapshot.version().getIngestStatus() : "UNKNOWN";
    }

    private boolean matchesFilters(
            KnowledgeBaseIngestedDocumentVersionResponse version,
            String keyword,
            String ingestStatus,
            String visibility) {
        if (StringUtils.hasText(ingestStatus)
                && !ingestStatus.trim().equalsIgnoreCase(version.getIngestStatus())) {
            return false;
        }
        if (StringUtils.hasText(visibility)
                && !visibility.trim().equalsIgnoreCase(version.getVisibility())) {
            return false;
        }
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(version.getVersionLabel(), normalizedKeyword)
                || contains(version.getVersionId(), normalizedKeyword)
                || contains(version.getSourceFileId(), normalizedKeyword)
                || contains(version.getIngestStatus(), normalizedKeyword)
                || contains(version.getVisibility(), normalizedKeyword)
                || contains(version.getContentHash(), normalizedKeyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private record AiDocumentSnapshot(
            KnowledgeBaseIngestedDocumentResponse document,
            KnowledgeBaseIngestedDocumentVersionResponse version) {
    }
}
