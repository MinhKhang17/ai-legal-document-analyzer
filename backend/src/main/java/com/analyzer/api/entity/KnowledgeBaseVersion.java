package com.analyzer.api.entity;

import com.analyzer.api.enums.KnowledgeReviewDecision;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_base_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_knowledge_base_version_no", columnNames = {"knowledge_base_entry_id", "version_no"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class KnowledgeBaseVersion extends BaseEntity {

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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "source_content_type")
    private String sourceContentType;

    @Column(name = "source_file_size")
    private Long sourceFileSize;

    @Column(name = "source_storage_path", columnDefinition = "TEXT")
    private String sourceStoragePath;

    @Column(name = "source_uploaded_at")
    private LocalDateTime sourceUploadedAt;

    @Column(name = "source_relative_path", columnDefinition = "TEXT")
    private String sourceRelativePath;

    @Column(name = "source_file_hash", length = 64)
    private String sourceFileHash;

    @Column(name = "ingest_source", length = 100)
    private String ingestSource;

    @Column(name = "neo4j_document_id")
    private String neo4jDocumentId;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "source_version_label")
    private String sourceVersionLabel;

    @Column(name = "effective_date")
    private String effectiveDate;

    @Column(name = "ingest_notified_at")
    private LocalDateTime ingestNotifiedAt;
}
