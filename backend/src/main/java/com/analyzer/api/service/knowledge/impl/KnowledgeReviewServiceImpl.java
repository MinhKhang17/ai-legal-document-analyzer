package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.dto.knowledge.KnowledgeReviewRequest;
import com.analyzer.api.client.KnowledgeBaseAiClient;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.KnowledgeReviewDecision;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.knowledge.KnowledgeReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgeReviewServiceImpl implements KnowledgeReviewService {

    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final KnowledgeBaseAiClient aiClient;

    @Override
    @Transactional
    public KnowledgeBaseVersionResponse review(String knowledgeBaseEntryId, KnowledgeReviewRequest request) {
        KnowledgeBaseEntry entry = findEntry(knowledgeBaseEntryId);
        KnowledgeBaseVersion version = findCurrentVersion(entry);
        User reviewer = userRepository.findById(entry.getCreatedBy().getId())
                .orElse(entry.getCreatedBy());

        version.setReviewDecision(request.getDecision());
        version.setReviewedBy(reviewer);
        LocalDateTime reviewedAt = LocalDateTime.now();
        version.setReviewedAt(reviewedAt);
        if (request.getDecision() == KnowledgeReviewDecision.REJECT) {
            version.setStatus(KnowledgeStatus.FAILED);
            version.setFailedReason(request.getNote());
            version.setVisibility(KnowledgeVisibility.PRIVATE);
            version.setActive(false);
            entry.setCurrentStatus(KnowledgeStatus.FAILED);
            entry.setActive(false);
        } else if (request.getDecision() == KnowledgeReviewDecision.APPROVE) {
            if (!syncAiLifecycle(entry, version, true)) {
                throw new ConflictException("Khong the duyet: AI knowledge metadata chua duoc cap nhat de public");
            }
            version.setStatus(KnowledgeStatus.PUBLIC);
            version.setFailedReason(null);
            version.setVisibility(KnowledgeVisibility.PUBLIC);
            version.setActive(true);
            version.setPublishedBy(reviewer);
            version.setPublishedAt(reviewedAt);
            entry.setCurrentStatus(KnowledgeStatus.PUBLIC);
            entry.setActive(true);
        } else {
            version.setStatus(KnowledgeStatus.REVIEWING);
            version.setFailedReason(request.getNote());
            version.setVisibility(KnowledgeVisibility.PRIVATE);
            version.setActive(false);
            entry.setCurrentStatus(KnowledgeStatus.REVIEWING);
            entry.setActive(false);
        }
        entryRepository.save(entry);
        return KnowledgeMappingSupport.toVersionResponse(versionRepository.save(version));
    }

    private KnowledgeBaseEntry findEntry(String id) {
        return entryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay knowledge base ID: " + id));
    }

    private KnowledgeBaseVersion findCurrentVersion(KnowledgeBaseEntry entry) {
        return versionRepository.findByKnowledgeBaseEntryIdAndVersionNo(entry.getId(), entry.getCurrentVersionNo())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay version hien tai cua knowledge base"));
    }

    private boolean syncAiLifecycle(KnowledgeBaseEntry entry, KnowledgeBaseVersion version, boolean makePublic) {
        if (!StringUtils.hasText(version.getNeo4jDocumentId())) {
            return false;
        }
        return aiClient.updateLifecycle(entry.getId(), version.getNeo4jDocumentId().trim(), makePublic);
    }
}
