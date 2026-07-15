package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.dto.knowledge.KnowledgeReviewRequest;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.KnowledgeReviewDecision;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.knowledge.KnowledgeReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgeReviewServiceImpl implements KnowledgeReviewService {

    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public KnowledgeBaseVersionResponse review(String knowledgeBaseEntryId, KnowledgeReviewRequest request) {
        KnowledgeBaseEntry entry = findEntry(knowledgeBaseEntryId);
        KnowledgeBaseVersion version = findCurrentVersion(entry);
        User reviewer = userRepository.findById(entry.getCreatedBy().getId())
                .orElse(entry.getCreatedBy());

        version.setReviewDecision(request.getDecision());
        version.setReviewedBy(reviewer);
        version.setReviewedAt(LocalDateTime.now());
        if (request.getDecision() == KnowledgeReviewDecision.REJECT) {
            version.setStatus(KnowledgeStatus.FAILED);
            version.setFailedReason(request.getNote());
            entry.setCurrentStatus(KnowledgeStatus.FAILED);
        } else {
            version.setStatus(KnowledgeStatus.REVIEWING);
            version.setFailedReason(request.getDecision() == KnowledgeReviewDecision.REQUEST_CHANGES ? request.getNote() : null);
            entry.setCurrentStatus(KnowledgeStatus.REVIEWING);
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
}
