package com.analyzer.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_context_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketContextSnapshot {
    @Id
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private LegalTicket ticket;

    @Column(name = "user_question", nullable = false, columnDefinition = "TEXT")
    private String userQuestion;

    @Column(name = "assistant_answer", columnDefinition = "TEXT")
    private String assistantAnswer;

    @Column(name = "conversation_title")
    private String conversationTitle;

    @Column(name = "citation_snapshot_json", columnDefinition = "TEXT")
    private String citationSnapshotJson;

    @Column(name = "document_snapshot_json", columnDefinition = "TEXT")
    private String documentSnapshotJson;

    @Column(name = "selected_message_snapshot_json", columnDefinition = "TEXT")
    private String selectedMessageSnapshotJson;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = "snapshot_" + UUID.randomUUID().toString().replace("-", "");
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
