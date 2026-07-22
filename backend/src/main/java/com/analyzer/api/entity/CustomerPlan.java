package com.analyzer.api.entity;

import com.analyzer.api.enums.PlanStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CustomerPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduled_subscription_plan_id")
    private SubscriptionPlan scheduledSubscriptionPlan;

    @Column(name = "plan_change_effective_at")
    private LocalDateTime planChangeEffectiveAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "used_quota", nullable = false)
    @Builder.Default
    private Integer usedQuota = 0;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = false;

    @Column(name = "usage_start_at")
    private LocalDateTime usageStartAt;

    @Column(name = "usage_end_at")
    private LocalDateTime usageEndAt;

    @Column(name = "billing_cycle_start_at")
    private LocalDateTime billingCycleStartAt;

    @Column(name = "billing_cycle_end_at")
    private LocalDateTime billingCycleEndAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "plan_name_snapshot")
    private String planNameSnapshot;

    @Column(name = "plan_type_snapshot")
    private String planTypeSnapshot;

    @Column(name = "price_snapshot")
    private java.math.BigDecimal priceSnapshot;

    @Column(name = "billing_cycle_days_snapshot")
    private Integer billingCycleDaysSnapshot;

    @Column(name = "analysis_limit_snapshot")
    private Integer analysisLimitSnapshot;

    @Column(name = "ai_token_limit_snapshot")
    private Integer aiTokenLimitSnapshot;

    @Column(name = "workspace_limit_snapshot")
    private Integer workspaceLimitSnapshot;

    @Column(name = "documents_per_workspace_limit_snapshot")
    private Integer documentsPerWorkspaceLimitSnapshot;

    @Column(name = "storage_limit_mb_snapshot")
    private Integer storageLimitMbSnapshot;

    @Column(name = "max_file_size_mb_snapshot")
    private Integer maxFileSizeMbSnapshot;

    @Column(name = "attached_documents_limit_snapshot")
    private Integer attachedDocumentsLimitSnapshot;

    @Column(name = "draft_contract_limit_snapshot")
    private Integer draftContractLimitSnapshot;

    @Column(name = "expert_ticket_limit_snapshot")
    private Integer expertTicketLimitSnapshot;

    @Column(name = "allow_contact_expert_ticket_snapshot")
    private Boolean allowContactExpertTicketSnapshot;

    @Transient
    private Integer remainingQuota;

    public Integer getRemainingQuota() {
        if (subscriptionPlan != null && subscriptionPlan.getMaxQuota() != null) {
            return subscriptionPlan.getMaxQuota() - (usedQuota != null ? usedQuota : 0);
        }
        return 0;
    }
}
