package com.analyzer.api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "accepted_terms", nullable = false)
    private Boolean acceptedTerms;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @Column(name = "email_verification_last_used_token")
    private String emailVerificationLastUsedToken;

    @Column(name = "email_verification_token_used_at")
    private java.time.LocalDateTime emailVerificationTokenUsedAt;

    @Column(name = "email_verification_token_expiry")
    private java.time.LocalDateTime emailVerificationTokenExpiry;

    @Column(name = "email_verification_requested_at")
    private java.time.LocalDateTime emailVerificationRequestedAt;

    @Column(name = "email_verified_at")
    private java.time.LocalDateTime emailVerifiedAt;

    @Column(name = "email_delivery_status", length = 16)
    private String emailDeliveryStatus;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "password_reset_deadline")
    private java.time.LocalDateTime passwordResetDeadline;

    private String specialty;

    @Column(name = "legal_domain")
    private String legalDomain;

    @Column(columnDefinition = "TEXT")
    private String description;
}
