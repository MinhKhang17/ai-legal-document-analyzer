package com.analyzer.api.entity;

import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.enums.ChatMessageType;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import com.analyzer.api.enums.ChatMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_session_status_created", columnList = "chat_session_id,status,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private ChatMessageType messageType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMessageStatus status;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "ai_model")
    private String aiModel;

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

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "citation_metadata_json", columnDefinition = "TEXT")
    private String citationMetadataJson;

    @Column(name = "context_snapshot_json", columnDefinition = "TEXT")
    private String contextSnapshotJson;

    @Column(name = "drafting_response_json", columnDefinition = "TEXT")
    private String draftingResponseJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_mode")
    private ChatMode resolvedMode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        LocalDateTime now = LocalDateTime.now();
        // Assigned IDs make Spring Data use merge(). Keep detached instances from
        // accidentally overwriting the immutable creation timestamp with null.
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }
}
