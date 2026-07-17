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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeUploadServiceImpl implements KnowledgeUploadService {

    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

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
                .extractedContent(sanitizeText(request.getExtractedContent()))
                .status(KnowledgeStatus.UPLOADED)
                .ingestStatus(KnowledgeStatus.PENDING)
                .visibility(KnowledgeVisibility.PRIVATE)
                .active(false)
                .build();
        return KnowledgeMappingSupport.toVersionResponse(versionRepository.save(version));
    }

    private String sanitizeText(String value) {
        return value == null ? null : value.replace("\u0000", "");
    }
}
