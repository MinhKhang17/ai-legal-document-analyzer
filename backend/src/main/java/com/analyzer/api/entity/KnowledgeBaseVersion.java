package com.analyzer.api.entity;

import com.analyzer.api.enums.KnowledgeReviewDecision;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_base_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_knowledge_base_version_no", columnNames = {"knowledge_base_entry_id", "version_no"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeBaseVersion {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_base_entry_id", nullable = false)
    private KnowledgeBaseEntry knowledgeBaseEntry;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private Document sourceDocument;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "extracted_content", columnDefinition = "TEXT", nullable = false)
    private String extractedContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KnowledgeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingest_status")
    private KnowledgeStatus ingestStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility")
    @Builder.Default
    private KnowledgeVisibility visibility = KnowledgeVisibility.PRIVATE;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = false;

    @Column(name = "ingested_at")
    private LocalDateTime ingestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingested_by_id")
    private User ingestedBy;

    @Column(name = "ingest_error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_decision")
    private KnowledgeReviewDecision reviewDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by_id")
    private User publishedBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by_id")
    private User archivedBy;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "failed_reason", columnDefinition = "TEXT")
    private String failedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
