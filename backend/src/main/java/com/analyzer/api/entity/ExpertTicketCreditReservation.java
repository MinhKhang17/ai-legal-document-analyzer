package com.analyzer.api.entity;

import com.analyzer.api.enums.TicketQuotaReservationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expert_ticket_credit_reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_expert_ticket_credit_ticket", columnNames = "ticket_id"),
        indexes = @Index(name = "idx_expert_ticket_credit_user_cycle", columnList = "user_id,quota_cycle,status"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpertTicketCreditReservation {
    @Id private String id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ticket_id", nullable = false) private LegalTicket ticket;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "quota_cycle", nullable = false, length = 64) private String quotaCycle;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private TicketQuotaReservationStatus status;
    @Column(name = "reserved_at") private LocalDateTime reservedAt;
    @Column(name = "consumed_at") private LocalDateTime consumedAt;
    @Column(name = "released_at") private LocalDateTime releasedAt;
    @Column(name = "release_reason") private String releaseReason;
    @Version private Long version;

    @PrePersist void onCreate() {
        if (id == null) id = "credit_" + UUID.randomUUID().toString().replace("-", "");
    }
}
