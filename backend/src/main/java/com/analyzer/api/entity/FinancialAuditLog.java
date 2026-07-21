package com.analyzer.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="financial_audit_logs", indexes=@Index(name="idx_financial_audit_created", columnList="created_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FinancialAuditLog {
    @Id private String id;
    @Column(nullable=false) private String action;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="actor_id") private User actor;
    @Column(name="entity_type", nullable=false) private String entityType;
    @Column(name="entity_id", nullable=false) private String entityId;
    @Column(name="old_values_json", columnDefinition="TEXT") private String oldValuesJson;
    @Column(name="new_values_json", columnDefinition="TEXT") private String newValuesJson;
    @Column(columnDefinition="TEXT") private String reason;
    @Column(name="request_id") private String requestId;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @PrePersist void create(){ if(id==null) id="finaudit_"+UUID.randomUUID().toString().replace("-",""); if(createdAt==null) createdAt=LocalDateTime.now(); }
}
