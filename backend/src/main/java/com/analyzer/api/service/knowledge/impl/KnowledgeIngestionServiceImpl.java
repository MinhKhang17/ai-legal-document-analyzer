package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.dto.knowledge.IngestKnowledgeRequest;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionJobResponse;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.KnowledgeIngestionJob;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.repository.knowledge.KnowledgeIngestionJobRepository;
import com.analyzer.api.service.knowledge.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeIngestionServiceImpl implements KnowledgeIngestionService {

    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;
    private final KnowledgeIngestionJobRepository jobRepository;

    @Override
    @Transactional
    public KnowledgeIngestionJobResponse ingest(String knowledgeBaseEntryId, IngestKnowledgeRequest request) {
        return jobRepository.findByRequestId(request.getRequestId())
                .map(KnowledgeMappingSupport::toJobResponse)
                .orElseGet(() -> createJob(knowledgeBaseEntryId, request));
    }

    private KnowledgeIngestionJobResponse createJob(String knowledgeBaseEntryId, IngestKnowledgeRequest request) {
        KnowledgeBaseEntry entry = entryRepository.findById(knowledgeBaseEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay knowledge base ID: " + knowledgeBaseEntryId));
        KnowledgeBaseVersion version = versionRepository
                .findByKnowledgeBaseEntryIdAndVersionNo(knowledgeBaseEntryId, entry.getCurrentVersionNo())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay version hien tai cua knowledge base"));

        LocalDateTime now = LocalDateTime.now();
        KnowledgeIngestionJob job = KnowledgeIngestionJob.builder()
                .id("kij_" + UUID.randomUUID().toString().replace("-", ""))
                .knowledgeBaseVersion(version)
                .requestId(request.getRequestId().trim())
                .status(KnowledgeStatus.INGESTED)
                .jobPayload(request.getJobPayload())
                .startedAt(now)
                .completedAt(now)
                .build();
        version.setStatus(KnowledgeStatus.INGESTED);
        entry.setCurrentStatus(KnowledgeStatus.INGESTED);
        versionRepository.save(version);
        entryRepository.save(entry);
        return KnowledgeMappingSupport.toJobResponse(jobRepository.save(job));
    }
}
