package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.dto.knowledge.ArchiveKnowledgeRequest;
import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.knowledge.KnowledgeArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgeArchiveServiceImpl implements KnowledgeArchiveService {

    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final KnowledgeBaseAiClient aiClient;

    @Override
    @Transactional
    public KnowledgeBaseVersionResponse archive(String knowledgeBaseEntryId, ArchiveKnowledgeRequest request) {
        KnowledgeBaseEntry entry = entryRepository.findById(knowledgeBaseEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay knowledge base ID: " + knowledgeBaseEntryId));
        KnowledgeBaseVersion version = versionRepository.findByKnowledgeBaseEntryIdAndVersionNo(entry.getId(), entry.getCurrentVersionNo())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay version hien tai cua knowledge base"));
        User archiver = userRepository.findById(entry.getCreatedBy().getId()).orElse(entry.getCreatedBy());
        if (Boolean.TRUE.equals(version.getActive())
                && !syncAiLifecycle(entry, version)) {
            throw new ConflictException("Khong the archive: AI knowledge metadata chua duoc cap nhat");
        }
        version.setStatus(KnowledgeStatus.ARCHIVED);
        version.setArchivedBy(archiver);
        version.setArchivedAt(LocalDateTime.now());
        version.setFailedReason(request.getReason());
        version.setVisibility(KnowledgeVisibility.PRIVATE);
        version.setActive(false);
        entry.setCurrentStatus(KnowledgeStatus.ARCHIVED);
        entry.setActive(false);
        entryRepository.save(entry);
        return KnowledgeMappingSupport.toVersionResponse(versionRepository.save(version));
    }

    private boolean syncAiLifecycle(KnowledgeBaseEntry entry, KnowledgeBaseVersion version) {
        if (!StringUtils.hasText(version.getNeo4jDocumentId())) {
            return false;
        }
        return aiClient.updateLifecycle(entry.getId(), version.getNeo4jDocumentId().trim(), false);
    }
}
