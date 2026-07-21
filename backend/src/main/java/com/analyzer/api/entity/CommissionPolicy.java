package com.analyzer.api.entity;

import com.analyzer.api.enums.CommissionPolicyStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="commission_policies", uniqueConstraints=@UniqueConstraint(name="uk_policy_source_request", columnNames="source_change_request_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CommissionPolicy {
    @Id private String id;
    @Column(nullable=false, precision=7, scale=6) private BigDecimal rate;
    @Column(name="effective_from", nullable=false) private LocalDate effectiveFrom;
    @Column(name="effective_to") private LocalDate effectiveTo;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private CommissionPolicyStatus status;
    @Column(columnDefinition="TEXT") private String reason;
    @Column(name="source_change_request_id") private String sourceChangeRequestId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private User createdBy;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @Column(name="activated_at") private LocalDateTime activatedAt;
    @Version private Long version;
    @PrePersist void create(){ if(id==null) id="policy_"+UUID.randomUUID().toString().replace("-",""); if(createdAt==null) createdAt=LocalDateTime.now(); }
}
