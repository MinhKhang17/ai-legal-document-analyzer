package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.dto.knowledge.UploadKnowledgeRequest;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.knowledge.KnowledgeUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeUploadServiceImpl implements KnowledgeUploadService {

    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    @Value("${app.storage.upload-root:uploads}")
    private String uploadRoot;

    @Override
    @Transactional
    public KnowledgeBaseVersionResponse upload(UploadKnowledgeRequest request) {
        User createdBy = userRepository.findById(request.getCreatedById())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay nguoi tao ID: " + request.getCreatedById()));
        Workspace workspace = request.getWorkspaceId() == null ? null : workspaceRepository.findById(String.valueOf(request.getWorkspaceId()))
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay workspace ID: " + request.getWorkspaceId()));

        KnowledgeBaseEntry entry = entryRepository.findByCode(request.getCode().trim())
                .orElseGet(() -> KnowledgeBaseEntry.builder()
                        .id("kb_" + UUID.randomUUID().toString().replace("-", ""))
                        .code(request.getCode().trim())
                        .createdBy(createdBy)
                        .active(false)
                        .build());

        int nextVersionNo = entry.getCurrentVersionNo() == null ? 1 : entry.getCurrentVersionNo() + 1;
        if (entry.getCreatedAt() == null) {
            nextVersionNo = 1;
        }
        entry.setTitle(request.getTitle().trim());
        entry.setCategory(request.getCategory().trim());
        entry.setScope(request.getScope());
        entry.setWorkspace(workspace);
        entry.setCurrentVersionNo(nextVersionNo);
        entry.setCurrentStatus(KnowledgeStatus.UPLOADED);
        entry.setActive(false);
        KnowledgeBaseEntry savedEntry = entryRepository.save(entry);

        KnowledgeBaseVersion version = KnowledgeBaseVersion.builder()
                .id("kbv_" + UUID.randomUUID().toString().replace("-", ""))
                .knowledgeBaseEntry(savedEntry)
                .versionNo(nextVersionNo)
                .rawContent(sanitizeText(request.getRawContent()))
                .extractedContent(sanitizeText(request.getExtractedContent()) == null ? "" : sanitizeText(request.getExtractedContent()))
                .description(request.getDescription() == null ? null : request.getDescription().trim())
                .status(KnowledgeStatus.UPLOADED)
                .ingestStatus(KnowledgeStatus.PENDING)
                .visibility(KnowledgeVisibility.PRIVATE)
                .active(false)
                .build();
        return KnowledgeMappingSupport.toVersionResponse(versionRepository.save(version));
    }

    @Override
    @Transactional
    public KnowledgeBaseVersionResponse storeSourceFile(String entryId, MultipartFile file) throws IOException {
        KnowledgeBaseVersion version = currentVersion(entryId);
        String fileName = safeSourceFileName(file.getOriginalFilename());
        Path directory = Path.of(uploadRoot, "knowledge-source", entryId, version.getId()).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) throw new IllegalArgumentException("INVALID_SOURCE_FILE_PATH");
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        version.setOriginalFileName(fileName);
        version.setSourceContentType(file.getContentType());
        version.setSourceFileSize(file.getSize());
        version.setSourceStoragePath(target.toString());
        version.setSourceUploadedAt(LocalDateTime.now());
        return KnowledgeMappingSupport.toVersionResponse(versionRepository.save(version));
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadSourceFile(String entryId) {
        KnowledgeBaseVersion version = currentVersion(entryId);
        if (version.getSourceStoragePath() == null) throw new ResourceNotFoundException("KNOWLEDGE_SOURCE_FILE_NOT_FOUND");
        FileSystemResource resource = new FileSystemResource(version.getSourceStoragePath());
        if (!resource.exists() || !resource.isReadable()) throw new ResourceNotFoundException("KNOWLEDGE_SOURCE_FILE_NOT_FOUND");
        return resource;
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeBaseVersionResponse getCurrentVersion(String entryId) {
        return KnowledgeMappingSupport.toVersionResponse(currentVersion(entryId));
    }

    private KnowledgeBaseVersion currentVersion(String entryId) {
        KnowledgeBaseEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("KNOWLEDGE_BASE_NOT_FOUND"));
        return versionRepository.findByKnowledgeBaseEntryIdAndVersionNo(entryId, entry.getCurrentVersionNo())
                .orElseThrow(() -> new ResourceNotFoundException("KNOWLEDGE_BASE_VERSION_NOT_FOUND"));
    }

    private String sanitizeText(String value) {
        return value == null ? null : value.replace("\u0000", "");
    }

    private String safeSourceFileName(String originalFileName) {
        String normalized = StringUtils.cleanPath(
                StringUtils.hasText(originalFileName) ? originalFileName.replace('\\', '/') : "knowledge-source");
        String baseName = StringUtils.getFilename(normalized);
        if (!StringUtils.hasText(baseName)) baseName = "knowledge-source";
        baseName = baseName.replaceAll("[\\x00-\\x1F\\x7F]", "_").trim();
        if (!StringUtils.hasText(baseName) || ".".equals(baseName) || "..".equals(baseName)) {
            throw new IllegalArgumentException("INVALID_SOURCE_FILE_NAME");
        }
        return baseName;
    }
}
