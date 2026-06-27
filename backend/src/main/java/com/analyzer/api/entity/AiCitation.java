package com.analyzer.api.entity;

import com.analyzer.api.enums.CitationSourceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_citations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCitation {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private CitationSourceType sourceType;

    @Column(name = "source_reference_id", nullable = false)
    private String sourceReferenceId;

    @Column(nullable = false)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String excerpt;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "score")
    private Double score;

    @Column(name = "uri")
    private String uri;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_ticket_id")
    private LegalTicket legalTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id")
    private ChatMessage chatMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
