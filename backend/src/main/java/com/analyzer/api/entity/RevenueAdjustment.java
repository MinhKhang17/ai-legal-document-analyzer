package com.analyzer.api.entity;

import com.analyzer.api.enums.RevenueAdjustmentType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="revenue_adjustments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RevenueAdjustment {
    @Id private String id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="original_period_id") private RevenuePeriod originalPeriod;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="applied_period_id", nullable=false) private RevenuePeriod appliedPeriod;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="expert_id", nullable=false) private User expert;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ticket_id") private LegalTicket ticket;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private RevenueAdjustmentType type;
    @Column(nullable=false, precision=19, scale=2) private BigDecimal amount;
    @Column(nullable=false, columnDefinition="TEXT") private String reason;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="created_by_id", nullable=false) private User createdBy;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @PrePersist void create(){ if(id==null) id="adjust_"+UUID.randomUUID().toString().replace("-",""); if(createdAt==null) createdAt=LocalDateTime.now(); }
}
