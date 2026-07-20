package com.analyzer.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_notifications", uniqueConstraints = @UniqueConstraint(
        name = "uk_system_notification_dedup", columnNames = "dedup_key"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SystemNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String type;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT", nullable = false) private String message;
    @Column(name = "entity_type", nullable = false) private String entityType;
    @Column(name = "entity_id", nullable = false) private String entityId;
    @Column(name = "dedup_key", nullable = false) private String dedupKey;
    @Column(name = "is_read", nullable = false) @Builder.Default private Boolean read = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
