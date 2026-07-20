package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.dto.knowledge.IngestKnowledgeRequest;
import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionJobResponse;
import com.analyzer.api.dto.knowledge.Neo4jKnowledgeRepairRequest;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileRequest;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileResponse;
import com.analyzer.api.dto.knowledge.UploadKnowledgeRequest;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.KnowledgeReviewDecision;
import com.analyzer.api.enums.KnowledgeScope;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.knowledge.KnowledgeIngestionService;
import com.analyzer.api.service.knowledge.KnowledgeServerBulkService;
import com.analyzer.api.service.knowledge.KnowledgeUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeServerBulkServiceImpl implements KnowledgeServerBulkService {

    private final KnowledgeUploadService uploadService;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final KnowledgeBaseAiClient aiClient;

    @Value("${app.knowledge.bulk-input-dir:/app/data/legal-bulk-input}")
    private String configuredBulkInputDir;

    @Override
    public ServerBulkIngestFileResponse ingestFile(ServerBulkIngestFileRequest request, Long adminId) {
        Path sourcePath = resolveSourcePath(request.getRelativePath());
        String fileHash = sha256(sourcePath);
        if (StringUtils.hasText(request.getFileHash()) && !fileHash.equalsIgnoreCase(request.getFileHash().trim())) {
            throw new IllegalArgumentException("BULK_FILE_HASH_MISMATCH");
        }

        Optional<KnowledgeBaseVersion> duplicate = versionRepository
                .findFirstBySourceFileHashOrderByCreatedAtDesc(fileHash);
        boolean alreadyIngested = duplicate
                .map(version -> version.getIngestStatus() == KnowledgeStatus.INGESTED
                        || version.getStatus() == KnowledgeStatus.PUBLIC)
                .orElse(false);
        if (alreadyIngested && !request.isForce()) {
            KnowledgeBaseVersion existing = duplicate.get();
            if (existing.getStatus() != KnowledgeStatus.PUBLIC) {
                User admin = findAdmin(adminId);
                if (!autoPublish(existing, admin)) {
                    return response(request.getRelativePath(), fileHash, "FAILED", "EXISTS",
                            existing.getStatus(), "FAILED", existing, null,
                            "neo4j_lifecycle_update_failed");
                }
            }
            return response(request.getRelativePath(), fileHash, "SKIPPED", "EXISTS",
                    existing.getStatus(), "COMPLETED",
                    existing, null, "duplicate_hash");
        }
        if (request.isDryRun()) {
            return ServerBulkIngestFileResponse.builder()
                    .relativePath(request.getRelativePath()).fileHash(fileHash).status("DRY_RUN")
                    .backendMetadataStatus("WOULD_CREATE").postgresStatus("NOT_WRITTEN")
                    .neo4jStatus("NOT_WRITTEN").chunkCount(0).build();
        }

        User admin = findAdmin(adminId);
        String title = StringUtils.hasText(request.getTitle())
                ? request.getTitle().trim() : stripExtension(sourcePath.getFileName().toString());
        String code = duplicate.map(version -> version.getKnowledgeBaseEntry().getCode())
                .orElseGet(() -> normalizeCode(request.getCode(), request.getRelativePath(), fileHash));
        KnowledgeBaseVersionResponse uploaded = null;
        try {
            uploaded = uploadService.upload(UploadKnowledgeRequest.builder()
                    .code(code)
                    .title(title)
                    .category(StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : "LEGAL_SOURCE")
                    .scope(KnowledgeScope.GLOBAL)
                    .createdById(admin.getId())
                    .description("Server bulk ingest: " + request.getRelativePath())
                    .extractedContent("")
                    .build());
            uploadService.storeSourceFile(
                    uploaded.getKnowledgeBaseEntryId(), sourcePath, sourcePath.getFileName().toString(), contentType(sourcePath));
            KnowledgeBaseVersion version = versionRepository.findById(uploaded.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("KNOWLEDGE_BASE_VERSION_NOT_FOUND"));
            version.setSourceRelativePath(request.getRelativePath());
            version.setSourceFileHash(fileHash);
            version.setIngestSource(StringUtils.hasText(request.getSource()) ? request.getSource().trim() : "bulk-server");
            version.setSourceVersionLabel(request.getVersion());
            version.setEffectiveDate(request.getEffectiveDate());
            versionRepository.save(version);

            KnowledgeIngestionJobResponse job = ingestionService.ingest(
                    uploaded.getKnowledgeBaseEntryId(),
                    IngestKnowledgeRequest.builder()
                            .requestId("bulk-" + fileHash.substring(0, 16) + "-" + UUID.randomUUID().toString().replace("-", ""))
                            .jobPayload(request.getRelativePath())
                            .build(),
                    admin.getId());
            KnowledgeIngestionJobResponse completedJob = waitForTerminalJob(job.getId());
            KnowledgeBaseVersion completedVersion = versionRepository.findById(uploaded.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("KNOWLEDGE_BASE_VERSION_NOT_FOUND"));
            boolean completed = completedJob.getStatus() == KnowledgeStatus.INGESTED;
            boolean published = completed && autoPublish(completedVersion, admin);
            String errorMessage = completed && !published
                    ? "neo4j_lifecycle_update_failed" : completedJob.getErrorMessage();
            return response(request.getRelativePath(), fileHash, published ? "COMPLETED" : "FAILED", "CREATED",
                    completedVersion.getStatus(), published ? "COMPLETED" : "FAILED",
                    completedVersion, completedJob.getId(), errorMessage);
        } catch (Exception exception) {
            if (uploaded != null) {
                markVersionFailed(uploaded.getId(), exception);
                KnowledgeBaseVersion failedVersion = versionRepository.findById(uploaded.getId()).orElse(null);
                return response(request.getRelativePath(), fileHash, "FAILED", "CREATED", KnowledgeStatus.FAILED,
                        "FAILED", failedVersion, null, message(exception));
            }
            throw exception instanceof RuntimeException runtimeException
                    ? runtimeException : new IllegalStateException(exception);
        }
    }

    @Override
    public ServerBulkIngestFileResponse repairFromNeo4j(Neo4jKnowledgeRepairRequest request, Long adminId) {
        Optional<KnowledgeBaseVersion> existing = versionRepository.findFirstByNeo4jDocumentId(request.getNeo4jDocumentId());
        if (existing.isEmpty() && StringUtils.hasText(request.getFileHash())) {
            existing = versionRepository.findFirstBySourceFileHashOrderByCreatedAtDesc(request.getFileHash().trim());
        }
        if (existing.isEmpty() && StringUtils.hasText(request.getFileName())) {
            existing = versionRepository.findFirstByOriginalFileNameIgnoreCaseOrderByCreatedAtDesc(request.getFileName().trim());
        }
        if (existing.isEmpty() && StringUtils.hasText(request.getTitle())) {
            existing = entryRepository.findFirstByTitleIgnoreCase(request.getTitle().trim())
                    .flatMap(entry -> versionRepository.findByKnowledgeBaseEntryIdAndVersionNo(
                            entry.getId(), entry.getCurrentVersionNo()));
        }
        if (existing.isPresent()) {
            KnowledgeBaseVersion version = existing.get();
            return response(request.getFileName(), request.getFileHash(), "SKIPPED", "EXISTS",
                    version.getIngestStatus(), "COMPLETED", version, null, "metadata_exists");
        }
        if (request.isDryRun()) {
            return ServerBulkIngestFileResponse.builder()
                    .relativePath(request.getFileName()).fileHash(request.getFileHash()).status("DRY_RUN")
                    .backendMetadataStatus("WOULD_CREATE").postgresStatus("NOT_WRITTEN")
                    .neo4jStatus("EXISTS").neo4jDocumentId(request.getNeo4jDocumentId())
                    .chunkCount(request.getChunkCount()).build();
        }

        User admin = findAdmin(adminId);
        String title = StringUtils.hasText(request.getTitle()) ? request.getTitle().trim()
                : StringUtils.hasText(request.getFileName()) ? stripExtension(request.getFileName()) : request.getNeo4jDocumentId();
        String hashSeed = StringUtils.hasText(request.getFileHash()) ? request.getFileHash() : request.getNeo4jDocumentId();
        KnowledgeBaseVersionResponse uploaded = uploadService.upload(UploadKnowledgeRequest.builder()
                .code(normalizeCode(request.getCode(), title, hashSeed))
                .title(title)
                .category(StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : "LEGAL_SOURCE")
                .scope(KnowledgeScope.GLOBAL)
                .createdById(admin.getId())
                .description("Repaired from Neo4j metadata")
                .extractedContent("")
                .build());
        KnowledgeBaseVersion version = versionRepository.findById(uploaded.getId())
                .orElseThrow(() -> new ResourceNotFoundException("KNOWLEDGE_BASE_VERSION_NOT_FOUND"));
        version.setOriginalFileName(request.getFileName());
        version.setSourceFileHash(request.getFileHash());
        version.setIngestSource(StringUtils.hasText(request.getSource()) ? request.getSource().trim() : "bulk-server");
        version.setNeo4jDocumentId(request.getNeo4jDocumentId());
        version.setChunkCount(request.getChunkCount());
        version.setStatus(KnowledgeStatus.INGESTED);
        version.setIngestStatus(KnowledgeStatus.INGESTED);
        version.setIngestedAt(LocalDateTime.now());
        version.setIngestedBy(admin);
        version.setVisibility(KnowledgeVisibility.PRIVATE);
        version.setActive(false);
        KnowledgeBaseEntry entry = version.getKnowledgeBaseEntry();
        entry.setCurrentStatus(KnowledgeStatus.INGESTED);
        entry.setActive(false);
        versionRepository.save(version);
        entryRepository.save(entry);

        boolean lifecycleUpdated = autoPublish(version, admin);
        return response(request.getFileName(), request.getFileHash(), lifecycleUpdated ? "COMPLETED" : "FAILED",
                "CREATED", version.getStatus(), lifecycleUpdated ? "COMPLETED" : "FAILED", version, null,
                lifecycleUpdated ? null : "neo4j_lifecycle_update_failed");
    }

    private KnowledgeIngestionJobResponse waitForTerminalJob(String jobId) {
        while (true) {
            KnowledgeIngestionJobResponse job = ingestionService.getJob(jobId);
            if (job.getStatus() == KnowledgeStatus.INGESTED || job.getStatus() == KnowledgeStatus.FAILED) {
                return job;
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Bulk ingest interrupted while waiting for AI worker", exception);
            }
        }
    }

    private User findAdmin(Long adminId) {
        return userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay admin ID: " + adminId));
    }

    private boolean autoPublish(KnowledgeBaseVersion version, User admin) {
        KnowledgeBaseEntry entry = version.getKnowledgeBaseEntry();
        if (!syncAiLifecycle(entry, version)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        version.setStatus(KnowledgeStatus.PUBLIC);
        version.setVisibility(KnowledgeVisibility.PUBLIC);
        version.setActive(true);
        version.setReviewDecision(KnowledgeReviewDecision.APPROVE);
        version.setReviewedBy(admin);
        version.setReviewedAt(now);
        version.setPublishedBy(admin);
        version.setPublishedAt(now);
        version.setErrorMessage(null);
        entry.setCurrentStatus(KnowledgeStatus.PUBLIC);
        entry.setActive(true);
        versionRepository.save(version);
        entryRepository.save(entry);
        return true;
    }

    private boolean syncAiLifecycle(KnowledgeBaseEntry entry, KnowledgeBaseVersion version) {
        if (!StringUtils.hasText(version.getNeo4jDocumentId())) {
            return false;
        }
        return aiClient.updateLifecycle(entry.getId(), version.getNeo4jDocumentId().trim(), true);
    }

    private Path resolveSourcePath(String relativePath) {
        Path root = Path.of(configuredBulkInputDir).toAbsolutePath().normalize();
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute()) throw new IllegalArgumentException("BULK_PATH_MUST_BE_RELATIVE");
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("BULK_PATH_OUTSIDE_INPUT_DIR");
        if (!Files.isRegularFile(target) || !Files.isReadable(target)) {
            throw new ResourceNotFoundException("BULK_SOURCE_FILE_NOT_FOUND: " + relativePath);
        }
        return target;
    }

    private String sha256(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash bulk source file", exception);
        }
    }

    private String normalizeCode(String requestedCode, String seed, String hash) {
        String value = StringUtils.hasText(requestedCode) ? requestedCode.trim() : stripExtension(seed);
        value = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.-]+", "_").replaceAll("_+", "_");
        value = value.replaceAll("^[_.-]+|[_.-]+$", "");
        if (!StringUtils.hasText(value)) value = "BULK_" + hash.substring(0, Math.min(12, hash.length())).toUpperCase(Locale.ROOT);
        return value.length() <= 100 ? value : value.substring(0, 87) + "_" + hash.substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String contentType(Path path) {
        try {
            String detected = Files.probeContentType(path);
            if (detected != null) return detected;
        } catch (IOException ignored) {
        }
        return switch (extension(path)) {
            case ".pdf" -> "application/pdf";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String value) {
        String name = Path.of(value).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void markVersionFailed(String versionId, Exception exception) {
        versionRepository.findById(versionId).ifPresent(version -> {
            String error = message(exception);
            version.setStatus(KnowledgeStatus.FAILED);
            version.setIngestStatus(KnowledgeStatus.FAILED);
            version.setErrorMessage(error);
            version.setVisibility(KnowledgeVisibility.PRIVATE);
            version.setActive(false);
            KnowledgeBaseEntry entry = version.getKnowledgeBaseEntry();
            entry.setCurrentStatus(KnowledgeStatus.FAILED);
            entry.setActive(false);
            versionRepository.save(version);
            entryRepository.save(entry);
        });
    }

    private ServerBulkIngestFileResponse response(
            String relativePath, String fileHash, String status, String metadataStatus,
            KnowledgeStatus postgresStatus, String neo4jStatus, KnowledgeBaseVersion version,
            String jobId, String errorMessage) {
        return ServerBulkIngestFileResponse.builder()
                .relativePath(relativePath).fileHash(fileHash).status(status)
                .backendMetadataStatus(metadataStatus)
                .postgresStatus(postgresStatus == null ? "UNKNOWN" : postgresStatus.name())
                .neo4jStatus(neo4jStatus)
                .knowledgeBaseEntryId(version == null ? null : version.getKnowledgeBaseEntry().getId())
                .knowledgeBaseVersionId(version == null ? null : version.getId())
                .jobId(jobId)
                .neo4jDocumentId(version == null ? null : version.getNeo4jDocumentId())
                .chunkCount(version == null || version.getChunkCount() == null ? 0 : version.getChunkCount())
                .errorMessage(errorMessage)
                .build();
    }

    private String message(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
