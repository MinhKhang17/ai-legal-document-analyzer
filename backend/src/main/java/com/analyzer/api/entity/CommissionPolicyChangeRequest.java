package com.analyzer.api.entity;

import com.analyzer.api.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="commission_policy_change_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CommissionPolicyChangeRequest {
    @Id private String id;
    @Column(name="old_rate_snapshot", nullable=false, precision=7, scale=6) private BigDecimal oldRateSnapshot;
    @Column(name="new_rate", nullable=false, precision=7, scale=6) private BigDecimal newRate;
    @Enumerated(EnumType.STRING) @Column(name="application_type", nullable=false, length=20) private CommissionApplicationType applicationType;
    @Column(name="effective_from", nullable=false) private LocalDate effectiveFrom;
    @Column(nullable=false, columnDefinition="TEXT") private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private CommissionChangeRequestStatus status;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="requested_by_id", nullable=false) private User requestedBy;
    @Column(name="requested_at", nullable=false, updatable=false) private LocalDateTime requestedAt;
    @Column(name="token_hash", unique=true) private String tokenHash;
    @Column(name="token_expires_at") private LocalDateTime tokenExpiresAt;
    @Column(name="verified_at") private LocalDateTime verifiedAt;
    @Version private Long version;
    @PrePersist void create(){ if(id==null) id="policyreq_"+UUID.randomUUID().toString().replace("-",""); if(requestedAt==null) requestedAt=LocalDateTime.now(); if(status==null) status=CommissionChangeRequestStatus.PENDING_EMAIL_VERIFICATION; }
}
