package com.analyzer.api.entity;

import com.analyzer.api.enums.ConversationScope;
import com.analyzer.api.enums.ConversationShareAccessMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_shares")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationShare {
    @Id private String id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ticket_id", nullable = false)
    private LegalTicket ticket;
    @Column(name = "session_id", nullable = false) private String sessionId;
    @Column(name = "share_token_hash", nullable = false, unique = true, length = 64) private String shareTokenHash;
    @Enumerated(EnumType.STRING) @Column(name = "share_scope", nullable = false) private ConversationScope shareScope;
    @Enumerated(EnumType.STRING) @Column(name = "access_mode", nullable = false) private ConversationShareAccessMode accessMode;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_id", nullable = false) private User createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist void onCreate() {
        if (id == null) id = "share_" + UUID.randomUUID().toString().replace("-", "");
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (accessMode == null) accessMode = ConversationShareAccessMode.PARTICIPANTS_ONLY;
    }
}
