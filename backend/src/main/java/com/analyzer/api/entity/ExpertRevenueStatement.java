package com.analyzer.api.entity;

import com.analyzer.api.enums.RevenueStatementStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="expert_revenue_statements", uniqueConstraints=@UniqueConstraint(name="uk_statement_period_expert", columnNames={"period_id","expert_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpertRevenueStatement {
    @Id private String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="period_id", nullable=false) private RevenuePeriod period;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="expert_id", nullable=false) private User expert;
    @Column(name="expert_name_snapshot", nullable=false) private String expertNameSnapshot;
    @Column(name="ticket_count", nullable=false) @Builder.Default private long ticketCount=0;
    @Column(name="gross_consultation_fee", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal grossConsultationFee=BigDecimal.ZERO;
    @Column(name="total_platform_fee", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal totalPlatformFee=BigDecimal.ZERO;
    @Column(name="total_expert_payout", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal totalExpertPayout=BigDecimal.ZERO;
    @Column(name="adjustment_amount", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal adjustmentAmount=BigDecimal.ZERO;
    @Column(name="final_payout", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal finalPayout=BigDecimal.ZERO;
    @Column(name="paid_amount", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal paidAmount=BigDecimal.ZERO;
    @Column(name="remaining_amount", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal remainingAmount=BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private RevenueStatementStatus status;
    @Column(name="generated_at", nullable=false) private LocalDateTime generatedAt;
    @Column(name="confirmed_at") private LocalDateTime confirmedAt;
    @Column(name="paid_at") private LocalDateTime paidAt;
    @Column(name="payment_reference") private String paymentReference;
    @Version private Long version;
    @PrePersist void create(){ if(id==null) id="stmt_"+UUID.randomUUID().toString().replace("-",""); if(status==null) status=RevenueStatementStatus.DRAFT; if(generatedAt==null) generatedAt=LocalDateTime.now(); }
}
