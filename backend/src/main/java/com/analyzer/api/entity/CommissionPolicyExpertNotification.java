package com.analyzer.api.entity;

import com.analyzer.api.enums.NotificationDeliveryStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="commission_policy_expert_notifications", uniqueConstraints=@UniqueConstraint(name="uk_policy_expert_notification", columnNames={"policy_id","expert_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CommissionPolicyExpertNotification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="policy_id", nullable=false) private CommissionPolicy policy;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="expert_id", nullable=false) private User expert;
    @Column(name="expert_email_snapshot", nullable=false) private String expertEmailSnapshot;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private NotificationDeliveryStatus status;
    @Column(name="retry_count", nullable=false) @Builder.Default private int retryCount=0;
    @Column(name="sent_at") private LocalDateTime sentAt;
    @Column(name="failed_at") private LocalDateTime failedAt;
    @Column(name="read_at") private LocalDateTime readAt;
}
