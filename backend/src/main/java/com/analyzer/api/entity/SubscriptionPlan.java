package com.analyzer.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.analyzer.api.enums.SubscriptionTier;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Column(name = "plan_type", nullable = false)
    private String planType;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier")
    private SubscriptionTier tier;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "max_quota", nullable = false)
    private Integer maxQuota;

    @Column(name = "ai_quota")
    private Integer aiQuota;

    @Column(name = "ticket_quota")
    private Integer ticketQuota;

    @Column(name = "max_workspaces")
    private Integer maxWorkspaces;

    @Column(name = "max_contracts_per_workspace")
    private Integer maxContractsPerWorkspace;

    @Column(name = "max_draft_contracts")
    private Integer maxDraftContracts;

    @Column(name = "feature_limits_json", columnDefinition = "TEXT")
    private String featureLimitsJson;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
