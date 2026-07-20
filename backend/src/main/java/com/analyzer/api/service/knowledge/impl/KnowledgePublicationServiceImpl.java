package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.dto.knowledge.PublishKnowledgeRequest;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.KnowledgeReviewDecision;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.knowledge.KnowledgePublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgePublicationServiceImpl implements KnowledgePublicationService {

    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final KnowledgeBaseAiClient aiClient;

    @Override
    @Transactional
    public KnowledgeBaseVersionResponse publish(String knowledgeBaseEntryId, PublishKnowledgeRequest request) {
        KnowledgeBaseEntry entry = entryRepository.findById(knowledgeBaseEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay knowledge base ID: " + knowledgeBaseEntryId));
        KnowledgeBaseVersion version = versionRepository.findByKnowledgeBaseEntryIdAndVersionNo(entry.getId(), entry.getCurrentVersionNo())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay version hien tai cua knowledge base"));
        if (version.getReviewDecision() != KnowledgeReviewDecision.APPROVE) {
            throw new ConflictException("Knowledge version chua duoc approve de publish");
        }
        User publisher = userRepository.findById(entry.getCreatedBy().getId()).orElse(entry.getCreatedBy());
        // Best-effort for legacy records that predate knowledge_base_id metadata.
        // Retrieval remains fail-closed because unsynchronized chunks stay private/inactive.
        syncAiLifecycle(entry, version, true);
        version.setStatus(KnowledgeStatus.PUBLIC);
        version.setPublishedBy(publisher);
        version.setPublishedAt(LocalDateTime.now());
        version.setVisibility(KnowledgeVisibility.PUBLIC);
        version.setActive(true);
        entry.setCurrentStatus(KnowledgeStatus.PUBLIC);
        entry.setActive(true);
        entryRepository.save(entry);
        return KnowledgeMappingSupport.toVersionResponse(versionRepository.save(version));
    }

    @Override
    @Transactional
    public KnowledgeBaseVersionResponse unpublish(String knowledgeBaseEntryId) {
        KnowledgeBaseEntry entry = entryRepository.findById(knowledgeBaseEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay knowledge base ID: " + knowledgeBaseEntryId));
        KnowledgeBaseVersion version = versionRepository
                .findByKnowledgeBaseEntryIdAndVersionNo(entry.getId(), entry.getCurrentVersionNo())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay version hien tai cua knowledge base"));
        if (!syncAiLifecycle(entry, version, false)) {
            throw new ConflictException("Khong the unpublish: AI knowledge metadata chua duoc cap nhat");
        }
        version.setVisibility(KnowledgeVisibility.PRIVATE);
        version.setActive(false);
        version.setPublishedAt(null);
        version.setPublishedBy(null);
        if (version.getIngestStatus() == KnowledgeStatus.INGESTED) {
            version.setStatus(KnowledgeStatus.INGESTED);
            entry.setCurrentStatus(KnowledgeStatus.INGESTED);
        }
        entry.setActive(false);
        entryRepository.save(entry);
        return KnowledgeMappingSupport.toVersionResponse(versionRepository.save(version));
    }

    private boolean syncAiLifecycle(KnowledgeBaseEntry entry, KnowledgeBaseVersion version, boolean makePublic) {
        if (version.getNeo4jDocumentId() != null
                && aiClient.updateLifecycle(entry.getId(), version.getNeo4jDocumentId(), makePublic)) {
            return true;
        }
        if (aiClient.updateLifecycle(entry.getId(), entry.getId(), makePublic)) {
            return true;
        }
        return version.getSourceDocument() != null
                && aiClient.updateLifecycle(entry.getId(), version.getSourceDocument().getId(), makePublic);
    }
}
