package com.analyzer.api.entity;

import com.analyzer.api.enums.RevenuePeriodStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "revenue_periods", uniqueConstraints = @UniqueConstraint(name = "uk_revenue_period_code", columnNames = "period_code"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RevenuePeriod {
    @Id private String id;
    @Column(name="period_code", nullable=false, length=20) private String periodCode;
    @Column(name="start_date", nullable=false) private LocalDate startDate;
    @Column(name="end_date", nullable=false) private LocalDate endDate;
    @Column(name="cutoff_at", nullable=false) private LocalDateTime cutoffAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private RevenuePeriodStatus status;
    @Column(name="closed_at") private LocalDateTime closedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="closed_by_id") private User closedBy;
    @Column(name="total_gross", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal totalGross=BigDecimal.ZERO;
    @Column(name="total_platform_fee", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal totalPlatformFee=BigDecimal.ZERO;
    @Column(name="total_expert_payout", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal totalExpertPayout=BigDecimal.ZERO;
    @Column(name="total_adjustments", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal totalAdjustments=BigDecimal.ZERO;
    @Column(name="total_final_payout", precision=19, scale=2, nullable=false) @Builder.Default private BigDecimal totalFinalPayout=BigDecimal.ZERO;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @Version private Long version;
    @PrePersist void create(){ if(id==null) id="revperiod_"+UUID.randomUUID().toString().replace("-",""); if(status==null) status=RevenuePeriodStatus.OPEN; if(createdAt==null) createdAt=LocalDateTime.now(); updatedAt=createdAt; }
    @PreUpdate void update(){ updatedAt=LocalDateTime.now(); }
}
