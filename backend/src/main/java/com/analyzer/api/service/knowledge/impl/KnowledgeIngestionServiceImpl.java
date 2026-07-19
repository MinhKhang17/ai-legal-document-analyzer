package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.dto.knowledge.IngestKnowledgeRequest;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionJobResponse;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionProgressRequest;
import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.KnowledgeIngestionJob;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.knowledge.KnowledgeBaseEntryRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.repository.knowledge.KnowledgeIngestionJobRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.knowledge.KnowledgeIngestNotificationService;
import com.analyzer.api.service.knowledge.event.KnowledgeIngestionDispatchRequested;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.analyzer.api.service.knowledge.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeIngestionServiceImpl implements KnowledgeIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeIngestionServiceImpl.class);

    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseVersionRepository versionRepository;
    private final KnowledgeIngestionJobRepository jobRepository;
    private final UserRepository userRepository;
    private final KnowledgeIngestNotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public KnowledgeIngestionJobResponse ingest(String knowledgeBaseEntryId, IngestKnowledgeRequest request, Long adminId) {
        return jobRepository.findByRequestId(request.getRequestId())
                .map(KnowledgeMappingSupport::toJobResponse)
                .orElseGet(() -> createJob(knowledgeBaseEntryId, request, adminId));
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeIngestionJobResponse getJob(String jobId) {
        return KnowledgeMappingSupport.toJobResponse(jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay ingest job ID: " + jobId)));
    }

    @Override
    @Transactional
    public KnowledgeIngestionJobResponse updateProgress(String jobId, KnowledgeIngestionProgressRequest request) {
        KnowledgeIngestionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay ingest job ID: " + jobId));
        KnowledgeBaseVersion version = job.getKnowledgeBaseVersion();
        KnowledgeBaseEntry entry = version.getKnowledgeBaseEntry();
        KnowledgeStatus nextStatus;
        try {
            nextStatus = KnowledgeStatus.valueOf((request.getStatus() == null ? "PROCESSING" : request.getStatus()).toUpperCase());
        } catch (IllegalArgumentException ex) {
            nextStatus = KnowledgeStatus.PROCESSING;
        }
        int progress = Math.max(0, Math.min(100, request.getProgressPercent() == null ? 0 : request.getProgressPercent()));
        job.setProgressPercent(progress);
        job.setStatus(nextStatus);
        if (nextStatus == KnowledgeStatus.INGESTED) {
            LocalDateTime now = LocalDateTime.now();
            boolean firstSuccessfulCallback = version.getIngestNotifiedAt() == null;
            job.setProgressPercent(100);
            job.setCompletedAt(now);
            job.setErrorMessage(null);
            version.setStatus(KnowledgeStatus.INGESTED);
            version.setIngestStatus(KnowledgeStatus.INGESTED);
            version.setIngestedAt(now);
            version.setIngestedBy(job.getIngestedBy());
            version.setErrorMessage(null);
            entry.setCurrentStatus(KnowledgeStatus.INGESTED);
            if (firstSuccessfulCallback) {
                version.setIngestNotifiedAt(now);
                try {
                    notificationService.notifyFirstSuccessfulIngest(job, entry, version, now);
                } catch (Exception exception) {
                    logger.warn("Unable to deliver knowledge ingest notification jobId={}: {}", job.getId(), exception.getMessage());
                }
            }
        } else if (nextStatus == KnowledgeStatus.FAILED) {
            String message = request.getErrorMessage() == null ? "Ingest failed" : request.getErrorMessage();
            job.setProgressPercent(100);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(message);
            version.setStatus(KnowledgeStatus.FAILED);
            version.setIngestStatus(KnowledgeStatus.FAILED);
            version.setErrorMessage(message);
            version.setVisibility(com.analyzer.api.enums.KnowledgeVisibility.PRIVATE);
            version.setActive(false);
            entry.setCurrentStatus(KnowledgeStatus.FAILED);
            entry.setActive(false);
        } else {
            job.setStatus(KnowledgeStatus.PROCESSING);
            version.setStatus(KnowledgeStatus.PROCESSING);
            version.setIngestStatus(KnowledgeStatus.PROCESSING);
            entry.setCurrentStatus(KnowledgeStatus.PROCESSING);
        }
        versionRepository.save(version);
        entryRepository.save(entry);
        return KnowledgeMappingSupport.toJobResponse(jobRepository.save(job));
    }

    private KnowledgeIngestionJobResponse createJob(String knowledgeBaseEntryId, IngestKnowledgeRequest request, Long adminId) {
        KnowledgeBaseEntry entry = entryRepository.findById(knowledgeBaseEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay knowledge base ID: " + knowledgeBaseEntryId));
        KnowledgeBaseVersion version = versionRepository
                .findByKnowledgeBaseEntryIdAndVersionNo(knowledgeBaseEntryId, entry.getCurrentVersionNo())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay version hien tai cua knowledge base"));

        LocalDateTime now = LocalDateTime.now();
        User admin = adminId == null ? entry.getCreatedBy() : userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay admin ID: " + adminId));
        KnowledgeIngestionJob job = KnowledgeIngestionJob.builder()
                .id("kij_" + UUID.randomUUID().toString().replace("-", ""))
                .knowledgeBaseVersion(version)
                .requestId(request.getRequestId().trim())
                .status(KnowledgeStatus.PROCESSING)
                .progressPercent(10)
                .jobPayload(request.getJobPayload())
                .startedAt(now)
                .ingestedBy(admin)
                .build();
        version.setStatus(KnowledgeStatus.PROCESSING);
        version.setIngestStatus(KnowledgeStatus.PROCESSING);
        version.setVisibility(com.analyzer.api.enums.KnowledgeVisibility.PRIVATE);
        version.setActive(false);
        entry.setCurrentStatus(KnowledgeStatus.PROCESSING);
        entry.setActive(false);
        jobRepository.save(job);

        versionRepository.save(version);
        entryRepository.save(entry);
        KnowledgeIngestionJob savedJob = jobRepository.save(job);
        eventPublisher.publishEvent(new KnowledgeIngestionDispatchRequested(
                knowledgeBaseEntryId, savedJob.getId(), admin == null ? null : admin.getId()));
        return KnowledgeMappingSupport.toJobResponse(savedJob);
    }
}
