package com.analyzer.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_audit_logs", indexes = @Index(name = "idx_ticket_audit_ticket_created", columnList = "ticket_id,created_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketAuditLog {
    @Id private String id;
    @Column(name = "ticket_id", nullable = false) private String ticketId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id") private User actor;
    @Column(name = "actor_type", nullable = false) private String actorType;
    @Column(name = "action", nullable = false) private String action;
    @Column(name = "metadata_json", columnDefinition = "TEXT") private String metadataJson;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (id == null) id = "audit_" + UUID.randomUUID().toString().replace("-", ""); if (createdAt == null) createdAt = LocalDateTime.now(); if (actorType == null) actorType = actor == null ? "SYSTEM" : "USER"; }
}
