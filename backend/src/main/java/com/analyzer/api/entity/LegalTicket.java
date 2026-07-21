package com.analyzer.api.entity;

import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import com.analyzer.api.enums.ConversationScope;
import com.analyzer.api.enums.TicketPriority;
import com.analyzer.api.enums.TicketRecipientType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.math.BigDecimal;
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

    @Column(name = "ticket_code", unique = true)
    private String ticketCode;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "issue_fingerprint")
    private String issueFingerprint;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type")
    private LegalTicketType ticketType;

    @Column(name = "related_chat_session_id")
    private String relatedChatSessionId;

    @Column(name = "related_chat_message_id")
    private String relatedChatMessageId;

    @Column(name = "source_user_message_id")
    private String sourceUserMessageId;

    @Column(name = "source_assistant_message_id")
    private String sourceAssistantMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type")
    private TicketRecipientType recipientType;

    @Column(name = "title")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_scope")
    private ConversationScope conversationScope;

    @Column(name = "shared_document_ids_json", columnDefinition = "TEXT")
    private String sharedDocumentIdsJson;

    @Column(name = "focused_document_id")
    private String focusedDocumentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
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

    @Column(name = "consultation_fee", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal consultationFee = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "expert_payment_status", nullable = false)
    @Builder.Default
    private com.analyzer.api.enums.ExpertPaymentStatus expertPaymentStatus = com.analyzer.api.enums.ExpertPaymentStatus.UNPAID;

    @Column(name = "expert_paid_at")
    private LocalDateTime expertPaidAt;

    @Column(name = "commission_rate", precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "platform_fee", precision = 19, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "expert_payout", precision = 19, scale = 2)
    private BigDecimal expertPayout;

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
        if (this.ticketCode == null || this.ticketCode.isBlank()) {
            this.ticketCode = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (this.priority == null) this.priority = TicketPriority.NORMAL;
        if (this.recipientType == null) this.recipientType = TicketRecipientType.EXPERT;
        if (this.conversationScope == null) this.conversationScope = ConversationScope.SELECTED_RESPONSE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.deleted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
