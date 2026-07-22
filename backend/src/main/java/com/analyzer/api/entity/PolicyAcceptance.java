package com.analyzer.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "policy_acceptances", uniqueConstraints = @UniqueConstraint(
        name = "uk_policy_acceptance_user_versions", columnNames = {"user_id", "terms_version", "privacy_policy_version"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PolicyAcceptance {
    @Id
    @Column(length = 40)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "terms_version", nullable = false, length = 40)
    private String termsVersion;
    @Column(name = "privacy_policy_version", nullable = false, length = 40)
    private String privacyPolicyVersion;
    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;
    @Column(name = "ip_hash", length = 64)
    private String ipHash;
    @Column(name = "user_agent_hash", length = 64)
    private String userAgentHash;
}
