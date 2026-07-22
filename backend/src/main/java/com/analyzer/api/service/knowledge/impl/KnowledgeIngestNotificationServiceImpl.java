package com.analyzer.api.service.knowledge.impl;

import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.entity.KnowledgeIngestionJob;
import com.analyzer.api.entity.SystemNotification;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.repository.SystemNotificationRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.service.notification.EmailService;
import com.analyzer.api.service.knowledge.KnowledgeIngestNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgeIngestNotificationServiceImpl implements KnowledgeIngestNotificationService {

    private final SystemNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyFirstSuccessfulIngest(KnowledgeIngestionJob job, KnowledgeBaseEntry entry,
                                            KnowledgeBaseVersion version, LocalDateTime ingestedAt) {
        User recipient = job.getIngestedBy();
        if (recipient == null) {
            recipient = userRepository.findAllByRole_NameAndActiveTrue(RoleName.ADMIN).stream().findFirst().orElse(null);
        }
        if (recipient == null) return;
        String dedupKey = "KNOWLEDGE_INGESTED:" + job.getId();
        if (notificationRepository.existsByDedupKey(dedupKey)) return;
        notificationRepository.saveAndFlush(SystemNotification.builder()
                .type("KNOWLEDGE_INGESTED").recipientUser(recipient)
                .title("Knowledge base da ingest thanh cong")
                .message(entry.getTitle() + " (" + entry.getCode() + ") da san sang de review va publish.")
                .entityType("KNOWLEDGE_BASE_ENTRY").entityId(entry.getId()).dedupKey(dedupKey).read(false).build());
        emailService.sendKnowledgeIngestedEmailAsync(recipient.getEmail(), recipient.getFirstName(),
                entry.getTitle(), entry.getCode(), version.getId(), job.getId(), ingestedAt.toString());
    }
}
