package com.analyzer.api.entity;

import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "legal_tickets",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_legal_ticket_request_creator",
            columnNames = {"request_id", "created_by_id"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalTicket {

    @Id
    private String id;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "issue_fingerprint")
    private String issueFingerprint;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "should_suggest_ticket")
    private Boolean shouldSuggestTicket;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggestion_type")
    private SuggestionType suggestionType;

    @Column(name = "suggestion_reason", columnDefinition = "TEXT")
    private String suggestionReason;

    @Column(name = "missing_information", columnDefinition = "TEXT")
    private String missingInformation;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Column(name = "legal_domain")
    private String legalDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_action_hint")
    private UserActionHint userActionHint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LegalTicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_lawyer_id")
    private User assignedLawyer;

    // Snapshot fields
    @Column(name = "issue_title")
    private String issueTitle;

    @Column(name = "issue_summary", columnDefinition = "TEXT")
    private String issueSummary;

    @Column(name = "problematic_clause", columnDefinition = "TEXT")
    private String problematicClause;

    @Column(name = "clause_reference")
    private String clauseReference;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "ai_evidence", columnDefinition = "TEXT")
    private String aiEvidence;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    // Resolution and notes
    @Column(name = "expert_answer", columnDefinition = "TEXT")
    private String expertAnswer;

    @Column(name = "expert_internal_note", columnDefinition = "TEXT")
    private String expertInternalNote;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // Audit timestamps
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    @Column(name = "close_reason", columnDefinition = "TEXT")
    private String closeReason;

    @Column(name = "last_customer_message_at")
    private LocalDateTime lastCustomerMessageAt;

    @Column(name = "last_lawyer_message_at")
    private LocalDateTime lastLawyerMessageAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (this.id == null || this.id.isBlank()) {
            this.id = "ticket_" + UUID.randomUUID().toString().replace("-", "");
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.deleted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
