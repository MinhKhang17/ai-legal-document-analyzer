package com.analyzer.api.entity;

import com.analyzer.api.enums.EarlyPayoutStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="early_payout_requests", uniqueConstraints=@UniqueConstraint(name="uk_early_payout_request_code", columnNames="request_code"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EarlyPayoutRequest {
    @Id private String id;
    @Column(name="request_code", nullable=false) private String requestCode;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="expert_id", nullable=false) private User expert;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="period_id", nullable=false) private RevenuePeriod period;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="statement_id", nullable=false) private ExpertRevenueStatement statement;
    @Column(name="requested_amount", precision=19, scale=2, nullable=false) private BigDecimal requestedAmount;
    @Column(name="eligible_amount_snapshot", precision=19, scale=2, nullable=false) private BigDecimal eligibleAmountSnapshot;
    @Column(name="approved_amount", precision=19, scale=2) private BigDecimal approvedAmount;
    @Column(columnDefinition="TEXT", nullable=false) private String reason;
    @Column(name="expert_note", columnDefinition="TEXT") private String expertNote;
    @Column(name="admin_note", columnDefinition="TEXT") private String adminNote;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private EarlyPayoutStatus status;
    @Column(name="requested_at", nullable=false, updatable=false) private LocalDateTime requestedAt;
    @Column(name="reviewed_at") private LocalDateTime reviewedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by_id") private User reviewedBy;
    @Column(name="approved_at") private LocalDateTime approvedAt;
    @Column(name="rejected_at") private LocalDateTime rejectedAt;
    @Column(name="paid_at") private LocalDateTime paidAt;
    @Column(name="payment_reference") private String paymentReference;
    @Column(name="idempotency_key") private String idempotencyKey;
    @Version private Long version;
    @PrePersist void create(){ if(id==null) id="early_"+UUID.randomUUID().toString().replace("-",""); if(requestCode==null) requestCode="EP-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(); if(status==null) status=EarlyPayoutStatus.PENDING_ADMIN_REVIEW; if(requestedAt==null) requestedAt=LocalDateTime.now(); }
}
