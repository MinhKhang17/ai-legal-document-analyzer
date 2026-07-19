package com.analyzer.api.entity;

import com.analyzer.api.enums.PlanStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPlan {

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

    @Transient
    private Integer remainingQuota;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Integer getRemainingQuota() {
        if (subscriptionPlan != null && subscriptionPlan.getMaxQuota() != null) {
            return subscriptionPlan.getMaxQuota() - (usedQuota != null ? usedQuota : 0);
        }
        return 0;
    }

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
