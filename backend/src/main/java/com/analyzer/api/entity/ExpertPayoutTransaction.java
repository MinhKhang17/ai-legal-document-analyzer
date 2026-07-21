package com.analyzer.api.entity;

import com.analyzer.api.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="expert_payout_transactions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpertPayoutTransaction {
    @Id private String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="expert_id", nullable=false) private User expert;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="statement_id", nullable=false) private ExpertRevenueStatement statement;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="early_payout_request_id", unique=true) private EarlyPayoutRequest earlyPayoutRequest;
    @Column(nullable=false, precision=19, scale=2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private PayoutTransactionType type;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private PayoutTransactionStatus status;
    @Column(name="paid_at") private LocalDateTime paidAt;
    @Column(name="payment_reference") private String paymentReference;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="paid_by_id") private User paidBy;
    @Column(name="idempotency_key", unique=true) private String idempotencyKey;
    @PrePersist void create(){ if(id==null) id="payout_"+UUID.randomUUID().toString().replace("-",""); }
}
